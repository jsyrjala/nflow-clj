(ns nflow-clj.database.db-util
  (:require [camel-snake-kebab.core :refer [->kebab-case ->snake_case]]
            [camel-snake-kebab.extras :refer [transform-keys]])
  )

(defn clj->db [value]
  (->> value
       (transform-keys ->snake_case)))

(defn db->clj [value]
  (->> value
       (transform-keys ->kebab-case)))
