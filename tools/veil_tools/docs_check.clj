(ns veil-tools.docs-check
  "Advisory checks for stale documentation: unmentioned bb tasks and features
   added on the branch with no QA procedure and no stated reason for having none."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(def ^:private scenario-prefixes ["Background:" "Scenario Outline:" "Scenario:"])

(defn- scenario-heading? [trimmed-line]
  (boolean (some #(str/starts-with? trimmed-line %) scenario-prefixes)))

(defn extract-feature-description
  "Extract the description block of a feature file (between Feature: and the first
   line starting with Background:, Scenario: or Scenario Outline:). Returns the
   text with leading/trailing whitespace trimmed per line."
  [feature-text]
  (->> (str/split-lines feature-text)
       (map str/trim)
       (drop-while #(not (str/starts-with? % "Feature:")))
       rest
       (take-while (complement scenario-heading?))
       (str/join "\n")))

(defn- public-task? [task-key]
  (and (symbol? task-key)
       (not (str/starts-with? (name task-key) "-"))))

(defn extract-tasks
  "Extract task symbols from bb.edn text, excluding keyword keys and symbols
   starting with '-'. Returns sorted vector of task name strings."
  [bb-edn-text]
  (->> (:tasks (edn/read-string bb-edn-text))
       keys
       (filter public-task?)
       (map name)
       sort
       vec))

(defn- mention-pattern
  "Matches 'bb <task>' only when the next character can't extend the task name,
   so 'bb qa-all' never counts as mentioning 'qa'."
  [task]
  (re-pattern (str "bb\\s+" (java.util.regex.Pattern/quote task) "(?![A-Za-z0-9_-])")))

(defn mentioned-tasks
  "Check which known tasks are mentioned in testing-md-text as 'bb <task>'.
   Returns set of task name strings that are mentioned."
  [testing-md-text task-names]
  (set (filter #(re-find (mention-pattern %) testing-md-text) task-names)))

(defn- unmentioned-task-finding [task]
  (str "task \"" task "\" is not mentioned in docs/testing.md"))

(defn task-findings
  "Compare tasks in bb.edn with mentions in testing.md. Returns vector of
   finding strings, one per unmentioned task, ordered alphabetically by task name."
  [bb-edn-text testing-md-text]
  (let [tasks (extract-tasks bb-edn-text)
        mentioned (mentioned-tasks testing-md-text tasks)]
    (->> tasks
         (remove mentioned)
         (mapv unmentioned-task-finding))))

(defn extract-qa-none-line
  "Extract a 'QA: none' line from the feature description block. Returns the line
   with runs of whitespace collapsed (e.g. 'QA: none - reason'), or nil if not
   found. Only matches in the description block (the lines between Feature: and
   the first Scenario)."
  [feature-text]
  (some #(when (re-matches #"QA:\s*none(?:\s.*)?" %)
           (str/replace % #"\s+" " "))
        (str/split-lines (extract-feature-description feature-text))))

(defn- qa-none-has-reason?
  "True if the QA: none line has a non-blank reason after the optional dash."
  [qa-none-line]
  (not (str/blank? (str/replace-first qa-none-line #"QA:\s*none\s*-?" ""))))

(defn- feature-slug [path]
  (-> path
      (str/replace #"\.feature$" "")
      (str/replace #"^specs/features/" "")))

(defn- feature-finding
  "The finding for one added feature, or nil when it has a procedure or an
   opt-out that states a reason."
  [procedure-paths {:keys [path text]}]
  (let [slug (feature-slug path)
        qa-none-line (extract-qa-none-line text)]
    (cond
      (contains? procedure-paths (str "specs/qa/" slug ".edn")) nil
      (nil? qa-none-line) (str "feature \"" slug "\" has no QA procedure and no \"QA: none\" line")
      (qa-none-has-reason? qa-none-line) nil
      :else (str "feature \"" slug "\" has a \"QA: none\" opt-out with no reason"))))

(defn qa-findings
  "Check added features for QA procedures or opt-out lines with reasons.
   added-features is a sequence of {:path ... :text ...} maps.
   procedure-paths is a set of procedure file paths that exist.
   Returns vector of finding strings."
  [added-features procedure-paths]
  (vec (keep #(feature-finding procedure-paths %) added-features)))

(defn- print-report
  "One PASS line, or an ADVISORY header and one 4-space-indented line per
   finding: check-clean.sh counts the indented lines."
  [findings]
  (if (empty? findings)
    (println "  PASS  no unmentioned tasks or orphaned features")
    (do (println (str "  ADVISORY  " (count findings) " finding(s):"))
        (run! #(println (str "    " %)) findings))))

(def ^:private advisory-exit-code
  "Findings only advise, so the check never fails the gate."
  0)

(defn run
  "Run both checks on the given texts, print the findings, and return the exit
   code. Reading bb.edn, docs/testing.md and the feature files, and working out
   which features the branch added, is the shell wrapper's job."
  [bb-edn-text testing-md-text added-features procedure-paths]
  (print-report (concat (task-findings bb-edn-text testing-md-text)
                        (qa-findings added-features procedure-paths)))
  advisory-exit-code)
