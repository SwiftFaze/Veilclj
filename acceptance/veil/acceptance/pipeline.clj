(ns veil.acceptance.pipeline
  "Normal acceptance run, in one JVM: generate an entry point per IR file,
  load them all, run them with clojure.test. Exit 0 only if every scenario
  passes."
  (:require [clojure.test :as test]
            [veil.acceptance.generator :as generator]))

(defn -main [output-dir & ir-paths]
  (let [entry-points (mapv #(generator/generate! % output-dir) ir-paths)
        _            (run! (comp load-file :path) entry-points)
        result       (apply test/run-tests (map :ns entry-points))]
    (shutdown-agents)
    (System/exit (if (zero? (+ (:fail result) (:error result))) 0 1))))
