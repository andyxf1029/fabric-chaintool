;; Licensed to the Apache Software Foundation (ASF) under one
;; or more contributor license agreements.  See the NOTICE file
;; distributed with this work for additional information
;; regarding copyright ownership.  The ASF licenses this file
;; to you under the Apache License, Version 2.0 (the
;; "License"); you may not use this file except in compliance
;; with the License.  You may obtain a copy of the License at
;;
;;   http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing,
;; software distributed under the License is distributed on an
;; "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
;; KIND, either express or implied.  See the License for the
;; specific language governing permissions and limitations
;; under the License.
(ns obcc.dar.write
  (:refer-clojure :exclude [import])
  (:import [org.apache.commons.io.input TeeInputStream]
           [org.apache.commons.io.output ByteArrayOutputStream]
           [java.util.zip GZIPOutputStream])
  (:require [flatland.protobuf.core :as fl]
            [clojure.java.io :as io]
            [doric.core :as doric]
            [pandect.algo.sha1 :refer :all]
            [pandect.algo.sha256 :refer :all]
            [obcc.dar.types :refer :all]
            [obcc.dar.ls :refer :all]))

(defn findfiles [path]
  (->> path file-seq (filter #(.isFile %))))

;;--------------------------------------------------------------------------------------
;; convertfile - takes a basepath (string) and file (handle) and returns a tuple containing
;; {handle path}
;;
;; handle: the raw io/file handle as passed in via 'file'
;; path: the relative path of the file w.r.t. the root of the archive
;;--------------------------------------------------------------------------------------
(defn convertfile [basepath file]
  (let [basepathlen (->> basepath io/file .getAbsolutePath count inc)
        fqpath (->> file .getAbsolutePath)
        path (subs fqpath basepathlen)]
    {:handle file :path path}))

;;--------------------------------------------------------------------------------------
;; import - takes a file handle and returns a tuple containing [sha1 size data]
;;
;; sha1: a string containing the computed sha1 of the raw uncompressed file contents
;; size: the raw uncompressed size of the file as it existed on the filesystem
;; data: a byte-array containing the compressed binary data imported from the filesystem
;;--------------------------------------------------------------------------------------
(defn import [file]
  (let [os (ByteArrayOutputStream.)
        [sha size] (with-open [is (io/input-stream file) ;; FIXME - validate maximum file size supported
                               gzipper (GZIPOutputStream. os)
                               tee (TeeInputStream. is gzipper)]
                     [(sha1 tee) (.length file)])] ;; FIXME - prefer to get the length from the stream
    [sha size (.toByteArray os)]))

;;--------------------------------------------------------------------------------------
;; buildfiles - takes a basepath string, and a vector of spec strings, and builds
;; a sorted list of {:handle :path} structures.
;;
;; Spec entires can be either an explicit file or a directory, both of which are
;; implicitly relative to basepath.  E.g. ["/path/to/foo" ["bar" "baz.conf"]]
;; would import ("/path/to/foo/bar" "/path/to/foo/baz.conf").  If any spec is a
;; directory it will be recursively expanded.
;;
;; The resulting structure will consist of an io/file under :handle, and a :path
;; with the basepath removed, sorted by :path (for determinisim)
;;--------------------------------------------------------------------------------------
(defn buildfiles [basepath spec]
  (let [handles (flatten (map #(findfiles (io/file basepath %)) spec))
        descriptors (map #(convertfile basepath %) handles)]
    (sort-by :path descriptors)))

;;--------------------------------------------------------------------------------------
;; buildentry - builds a protobuf "Entry" object based on the tuple as emitted by (convertfile)
;;--------------------------------------------------------------------------------------
(defn buildentry [{:keys [path handle]}]
  (let [[sha size payload] (import handle)]
    (fl/protobuf Entries :path path :size size :sha1 sha :data payload)))

;;--------------------------------------------------------------------------------------
;; buildentries - builds a list of protobuf "Entry" objects based on an input list
;; of {handle path} tuples.  The output list will respect the input list order, and
;; it is important that the input list be pre-sorted in a deterministic manner if
;; the serialized output is expected to be deterministic as well.
;;--------------------------------------------------------------------------------------
(defn buildentries [files]
  (map buildentry files))

(defn write [rootpath filespec outputfile]
  (println "Writing CCA to:" (.getAbsolutePath outputfile))
  (println "Using path" rootpath (str filespec))
  (let [files (buildfiles rootpath filespec)
        header (fl/protobuf Header :magic (:magic CompatVersion) :version (:version CompatVersion))
        entries (buildentries files)
        compression (fl/protobuf Compression :description "gzip")
        payload (fl/protobuf Payload :compression compression :entries entries) ;; FIXME: need Type enum set
        archive (fl/protobuf Archive :payload (fl/protobuf-dump payload))]

    ;; ensure the path exists
    (io/make-parents outputfile)

    ;; emit our output
    (with-open [os (io/output-stream outputfile :truncate true)]
      (fl/protobuf-write os header archive)))

  ;; re-use the ls function to display the contents
  (ls outputfile))
