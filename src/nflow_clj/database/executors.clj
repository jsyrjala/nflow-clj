(ns nflow-clj.database.executors
  (:require [hugsql.core :as hugsql]
            [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [nflow-clj.database.db-util :as db-util]
            )
)

(hugsql/def-db-fns "sql/executors.sql")

(defn get-executors [db executor-group]
  (db-util/execute query-executors
                   db {:executor_group executor-group}))

(defn create-executor! [db executor]
  (-> (db-util/execute insert-executor!
                       db executor)
      db-util/inserted-id))

(defn update-activity! [db executor-id expires-in]
  (db-util/execute update-executor-activity!
                   db
                   {:executor_id executor-id
                    :expires_in  expires-in}))

(defn mark-shutdown! [db executor-group executor-id]
  (db-util/execute update-shutdown!
                   db
                   {:executor-group executor-group
                    :executor-id executor-id}))
