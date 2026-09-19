(ns veil.ui.qa.runner
  "QA procedure runner: spawns the game with a script and checks the log."
  (:require [clojure.java.io :as io]
            [veil.ui.qa.procedure :as procedure]
            [veil.ui.qa.log :as log])
  (:gen-class))

(defn run-one
  "Run a single QA procedure and return its exit status (0 for pass, 1 for fail).
   Prints report lines and final result to stdout. Errors go to stderr."
  [slug]
  (let [proc-path (procedure/path slug)
        proc-file (io/file proc-path)]
    (if-not (.exists proc-file)
      (do
        (binding [*out* *err*]
          (println (procedure/unknown-error slug)))
        1)

      (let [proc-text (slurp proc-path)
            proc-result (procedure/parse proc-text)]
        (if (:error proc-result)
          (do
            (binding [*out* *err*]
              (println (:error proc-result)))
            1)

          (let [script-path (:script proc-result)
                log-path (str "target/qa/" slug ".log.edn")
                java-home (System/getProperty "java.home")
                java-exe (str java-home "/bin/java")
                cp (System/getProperty "java.class.path")
                proc (-> (ProcessBuilder. [java-exe "-cp" cp "clojure.main" "-m" "veil.main" "--keys" script-path "--log" log-path])
                         (.inheritIO)
                         (.start))
                timed-out? (not (.waitFor proc 60 java.util.concurrent.TimeUnit/SECONDS))]

            (if timed-out?
              (do
                (.destroyForcibly proc)
                (binding [*out* *err*]
                  (println "game did not finish within 60s"))
                1)

              (let [exit-code (.exitValue proc)]
                (if-not (zero? exit-code)
                  (do
                    (binding [*out* *err*]
                      (println (str "game exited with code " exit-code)))
                    1)

                  (let [log-text (slurp log-path)
                        log-result (log/parse log-text)]
                    (if (:error log-result)
                      (do
                        (binding [*out* *err*]
                          (println (:error log-result)))
                        1)

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
                        status))))))))))))

(defn -main [& args]
  (let [arg (first args)]
    (cond
      (= arg "--all")
      (let [qa-dir (io/file "specs/qa")
            file-names (if (.exists qa-dir) (.list qa-dir) [])
            slugs-result (procedure/slugs file-names)]
        (if (:error slugs-result)
          (do
            (binding [*out* *err*]
              (println (:error slugs-result)))
            (System/exit 1))
          (let [slugs-list (:slugs slugs-result)
                results (mapv (fn [slug] [slug (run-one slug)]) slugs-list)
                summary-result (procedure/summary results)]
            (println (:line summary-result))
            (System/exit (:status summary-result)))))

      (and arg (not (.startsWith arg "--")))
      (System/exit (run-one arg))

      :else
      (do
        (binding [*out* *err*]
          (println "Usage: bb qa <slug> | bb qa --all"))
        (System/exit 1)))))
