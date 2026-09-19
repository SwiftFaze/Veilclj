(ns veil.ui.qa.runner
  "QA procedure runner: spawns the game with a script and checks the log."
  (:require [clojure.java.io :as io]
            [veil.ui.qa.procedure :as procedure]
            [veil.ui.qa.log :as log])
  (:gen-class))

(defn -main [& args]
  (let [slug (first args)
        _ (when-not slug
            (binding [*out* *err*]
              (println "Usage: bb qa <slug>"))
            (System/exit 1))
        proc-path (procedure/path slug)
        proc-file (io/file proc-path)]
    (when-not (.exists proc-file)
      (binding [*out* *err*]
        (println (procedure/unknown-error slug)))
      (System/exit 1))

    (let [proc-text (slurp proc-path)
          proc-result (procedure/parse proc-text)]
      (when (:error proc-result)
        (binding [*out* *err*]
          (println (:error proc-result)))
        (System/exit 1))

      (let [script-path (:script proc-result)
            log-path (str "target/qa/" slug ".log.edn")
            java-home (System/getProperty "java.home")
            java-exe (str java-home "/bin/java")
            cp (System/getProperty "java.class.path")
            proc (-> (ProcessBuilder. [java-exe "-cp" cp "clojure.main" "-m" "veil.main" "--keys" script-path "--log" log-path])
                     (.inheritIO)
                     (.start))
            exit-code (.waitFor proc)]

        (when-not (zero? exit-code)
          (binding [*out* *err*]
            (println (str "game exited with code " exit-code)))
          (System/exit 1))

        (let [log-text (slurp log-path)
              log-result (log/parse log-text)]
          (when (:error log-result)
            (binding [*out* *err*]
              (println (:error log-result)))
            (System/exit 1))

          (let [entries (:entries log-result)
                expected (:expect proc-result)
                check-result (procedure/check entries expected)
                report (:report check-result)
                status (:status check-result)]

            (doseq [line (procedure/report-lines expected report)]
              (println line))

            (if (zero? status)
              (println (str "QA " slug ": PASS"))
              (let [missing-count (count (filter #{:missing} report))]
                (println (str "QA " slug ": FAIL (" missing-count " missing)"))))

            (System/exit status)))))))
