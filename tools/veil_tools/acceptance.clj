(ns veil-tools.acceptance
  "The acceptance pipeline: specs/features/*.feature -> JSON IR (APS parser)
  -> generated clojure.test entry points -> run, all under build/acceptance/."
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell]]
            [veil-tools.aps :as aps]))

(def features-dir "specs/features")
(def build-dir "build/acceptance")
(def generated-dir (str build-dir "/generated"))

(defn- features []
  (sort (map (comp fs/unixify str) (fs/glob features-dir "*.feature"))))

(defn- ir-path [feature]
  (str build-dir "/ir/" (fs/strip-ext (fs/file-name feature)) ".json"))

(defn- parse! [feature]
  (fs/create-dirs (fs/parent (ir-path feature)))
  (when-not (zero? (aps/run-tool "gherkin-parser" feature (ir-path feature)))
    (throw (ex-info (str "gherkin-parser failed on " feature) {:babashka/exit 1})))
  (ir-path feature))

(defn- jvm [& args]
  (:exit (apply shell {:continue true} "bb" "clojure" "-M:acceptance" args)))

(defn run-all
  "Normal run. Exit code 0 when every scenario of every feature passes."
  []
  (fs/delete-tree build-dir)
  (let [feature-files (features)]
    (if (empty? feature-files)
      (do (println "No features under" features-dir) 0)
      (apply jvm "-m" "veil.acceptance.pipeline" generated-dir
             (mapv parse! feature-files)))))

(defn mutate
  "Acceptance mutation (APS): mutates each feature's example values and
  checks the acceptance tests notice. Needs a fresh normal run for the
  generated entry points and metadata. Returns the worst exit code."
  [& mutator-args]
  (let [status (run-all)]
    (if-not (zero? status)
      status
      (reduce max 0
              (for [feature (features)]
                (apply aps/run-tool "gherkin-mutator"
                       "--feature" feature
                       "--work-dir" "build/acceptance-mutation"
                       "--generated-dir" generated-dir
                       "--runner-worker" "bb clojure -M:acceptance -m veil.acceptance.mutation-runner"
                       mutator-args))))))
