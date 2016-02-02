(ns cljparse.build.interface
  (:require [clojure.java.io :as io]
            [clojure.zip :as zip]
            [instaparse.core :as insta]
            [cljparse.config.parser :as config]))
            

(def grammar (insta/parser (io/resource "interface.bnf")
                           :auto-whitespace (insta/parser (io/resource "skip.bnf"))))

(defn parse [intf] (->> intf grammar zip/vector-zip))

(defn getinterfaces [config]
  (let [keys [[:configuration :provides] [:configuration :consumes]]]
        (->> (map #(config/find % config) keys) flatten (into #{}) (into '()))))

(defn open [path intf]
  (let [file (io/file path (str intf ".cci"))]
    (cond
      (.exists file)
      file

      :else
      (throw (Exception. (str (.getAbsolutePath file) " not found"))))))

(defn compileintf [path intf]
  (println "Compile " intf)
  (let [ipath (str path "/src/interfaces")
        opath (str path "/build/interfaces")
        tree (->> (open ipath intf) slurp parse)]
    (println tree)))

(defn compile [path config]
  (let [interfaces (getinterfaces config)]
    (map #(compileintf path %) interfaces)))


