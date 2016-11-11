(ns nflow-clj.database.instances
  (:require [hugsql.core :as hugsql]
            [clj-time.core :as time]
            [clojure.tools.logging :as log]
            [cheshire.core :as json]
            [nflow-clj.database.db-util :as db-util]
            ))

(hugsql/def-db-fns "sql/instances.sql")

(defn store-instance! [db instance]
  (-> (db-util/execute insert-instance! db instance)
      db-util/inserted-id))

(defn store-action! [db action]
  (-> (db-util/execute insert-action! db action)
      db-util/inserted-id))

(defn store-state! [db state]
  (-> (db-util/execute insert-state! db state)
      db-util/inserted-id))
