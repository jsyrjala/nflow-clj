(ns nflow-clj.util
  (:import (java.lang.management ManagementFactory)
           (java.net InetAddress)))

(defn process-id []
  (let [jvm-name (.getName (ManagementFactory/getRuntimeMXBean))]
    (first (.split jvm-name "@"))))

(defn hostname []
  (let [localhost (InetAddress/getLocalHost)
        hostname (.getCanonicalHostName localhost)]
    (subs hostname 0 (min 253 (count hostname)))))
