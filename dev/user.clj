(ns user
  (:require [nflow-clj.database.connection-pool :as pool]
            [nflow-clj.database.executors :as executors]
            [nflow-clj.util :as util]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [clojure.string :as string]
            )
  )

;; TODO need to do this somewhere
(require 'clj-time.jdbc)

(defn read-config [filename]
  (-> (slurp filename)
      (edn/read-string)))

(def config (read-config "./config.edn"))

(println "REPL ready")

(defonce db (pool/create-hikari-pool (-> config :database)))

(executors/get-executors db "foo")

(def executor {:host (util/hostname)
               :pid (util/process-id)
               :executor-group "foo"
               :expires-in 1000})

(def executor-id (executors/create-executor! db executor))
executor-id
(executors/update-activity! db executor-id 3600)

(def instance {
               :type "foo"
               :root_workflow_id nil
               :parent_workflow_id nil
               :parent_action_id nil
               :business_key "foo"
               :external_id "foo"
               :executor_group "foo"
               :status
               :state
               :state_text
               :next_activation
               })

(+ 1 1)