(ns nflow-clj.database.instances
  (:require [hugsql.core :as hugsql]
            [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [nflow-clj.database.db-util :as db-util :refer [execute inserted-id]]
            ))

(hugsql/def-db-fns "sql/instances.sql")

(defn store-instance! [db instance]
  (-> (execute insert-instance! db instance)
      inserted-id))

(defn store-action! [db action]
  (-> (execute insert-action! db action)
      inserted-id))

(defn store-state! [db state]
  (-> (execute insert-state! db state)
      inserted-id))

(defn get-recoverable-instances [db executor-group executor-id]
  (execute query-recoverable-instances db
           {:executor-group executor-group
            :executor-id executor-id
            }))

(defn get-processable-instances [db executor-group limit]
  (execute query-processable-instances db
           {:executor-group executor-group
            :limit limit}))