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
  (-> (query-executors db {:executor_group executor-group})
      db-util/db->clj))

(defn create-executor! [db executor]
  (-> (insert-executor! db
                        (-> executor
                            db-util/clj->db))
      db-util/db->clj))

(defn update-activity! [db executor-id expires-in]
  (update-executor-activity! db
                             (-> {:executor_id executor-id
                                  :expires_in expires-in}
                                 db-util/clj->db)))