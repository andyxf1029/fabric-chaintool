(ns example02.core
  (:require [cljs.nodejs :as nodejs]
            [example02.rpc :as rpc]))

(def pb (nodejs/require "protobufjs"))
(def builder (.newBuilder pb))

(defn- loadproto [name]
  (do
    (.loadProtoFile pb (str "./" name ".proto") builder)
    (.build builder name)))

(def init (loadproto "appinit"))
(def app (loadproto "org.hyperledger.chaincode.example02"))

(defn deploy [{:keys [host port args]}]
  (rpc/deploy {:host host
               :port port
               :id #js {:name "mycc"}
               :func "init"
               :args (init.Init. args)
               :cb (fn [resp] (println "Response:" resp))}))

(defn check-balance [{:keys [host port id]}]
  (rpc/query {:host host
              :port port
              :id #js {:name "mycc"}
              :func "org.hyperledger.chaincode.example02/query/1"
              :args (app.Entity. #js {:id id})
              :cb (fn [resp]
                    (if (= (->> resp :result :status) "OK")
                      (let [result (->> resp :result :message app.BalanceResult.decode64)]
                        (println "Success: Balance =" (.-balance result)))
                      ;; else
                      (println "Failure:" resp)))}))

(defn run [{:keys [host port] :as options}]
  (deploy {:host host
           :port port
           :args #js {:partyA #js {
                                   :entity "foo"
                                   :value 100
                                   }
                      :partyB #js {
                                   :entity "bar"
                                   :value 100
                                   }}})

  (check-balance {:host host
                  :port port
                  :id "foo"}))
