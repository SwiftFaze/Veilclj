(ns veil-tools.aps
  "Uncle Bob's Acceptance Pipeline Specification tools (gherkin-parser,
  gherkin-mutator), run from a checkout pinned to one commit. APS ships no
  deps.edn, so it can't be a git dependency; a pinned checkout under .cache/
  is the reproducible alternative (the same approach as htw-clj-six-pack)."
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell sh]]
            [clojure.string :as str]))

(def url "https://github.com/unclebob/Acceptance-Pipeline-Specification")
(def sha "accaa33d503340c56513ef387258f8da929ba902")
(def checkout ".cache/aps")

(defn- checked-out-sha []
  (when (fs/exists? (fs/path checkout ".git"))
    (str/trim (:out (sh "git" "-C" checkout "rev-parse" "HEAD")))))

(defn ensure-checkout! []
  (when-not (= sha (checked-out-sha))
    (when-not (fs/exists? checkout)
      (shell "git" "clone" "--quiet" url checkout))
    (shell "git" "-C" checkout "fetch" "--quiet" "origin")
    (shell "git" "-C" checkout "checkout" "--quiet" sha)))

(defn run-tool
  "Runs an APS CLI namespace (e.g. \"gherkin-parser\") in a child bb process.
  Returns the process exit code rather than throwing."
  [tool & args]
  (ensure-checkout!)
  (:exit (apply shell {:continue true}
                "bb" "-cp" (str checkout "/bb/src") "-m" (str "aps.cli." tool)
                args)))
