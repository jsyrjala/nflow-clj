(ns nflow-clj.database.db-util-test
  (:require [clojure.test :refer :all]
            [nflow-clj.database.db-util :refer :all])
  )

(testing "clj->db"
  (is (= (clj->db nil) {}))
  (is (= (clj->db {}) {}))
  (is (= (clj->db {:a 1}) {:a 1}))
  (is (= (clj->db {:a nil}) {:a nil}))
  (is (= (clj->db {:a nil :b 1}) {:a nil :b 1}))
  (is (= (clj->db {:a :nflow-instance-type/created}) {:a "created"}))
  )