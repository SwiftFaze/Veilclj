(ns veil-tools.docs-check
  "Advisory checks for stale documentation: unmentioned bb tasks and features
   added on the branch with no QA procedure and no stated reason for having none."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(defn extract-feature-description
  "Extract the description block of a feature file (between Feature: and the first
   line starting with Background:, Scenario: or Scenario Outline:). Returns the
   text with leading/trailing whitespace trimmed per line."
  [feature-text]
  (let [lines (str/split feature-text #"\n")
        feature-idx (some (fn [i] (when (str/starts-with? (str/trim (nth lines i)) "Feature:") i))
                          (range (count lines)))
        scenario-idx (when feature-idx
                       (some (fn [i]
                               (when (or (str/starts-with? (str/trim (nth lines i)) "Background:")
                                         (str/starts-with? (str/trim (nth lines i)) "Scenario:"))
                                 i))
                             (range (inc feature-idx) (count lines))))
        desc-lines (if feature-idx
                     (if scenario-idx
                       (subvec (vec lines) (inc feature-idx) scenario-idx)
                       (subvec (vec lines) (inc feature-idx)))
                     [])]
    (str/join "\n" (map str/trim desc-lines))))

(defn extract-tasks
  "Extract task symbols from bb.edn text, excluding keyword keys and symbols
   starting with '-'. Returns sorted vector of task name strings."
  [bb-edn-text]
  (let [edn (edn/read-string bb-edn-text)
        tasks-map (:tasks edn)
        task-names (->> (keys tasks-map)
                        (filter (fn [k]
                                  (and (symbol? k)
                                       (not (str/starts-with? (name k) "-")))))
                        (map name)
                        sort)]
    task-names))

(defn mentioned-tasks
  "Extract task names mentioned in testing-md-text as 'bb <task>' where the char
   after the task name can't be part of a task name. Returns set of task name strings."
  [testing-md-text]
  (let [pattern #"bb\s+([a-z]+(?:-[a-z]+)?)(?![a-z0-9_-])"]
    (->> (re-seq pattern testing-md-text)
         (map second)
         set)))

(defn task-findings
  "Compare tasks in bb.edn with mentions in testing.md. Returns vector of
   finding strings, one per unmentioned task, ordered alphabetically by task name."
  [bb-edn-text testing-md-text]
  (let [tasks (extract-tasks bb-edn-text)
        mentioned (mentioned-tasks testing-md-text)
        unmentioned (filter (fn [t] (not (mentioned t))) tasks)]
    (vec (map (fn [t] (str "task \"" t "\" is not mentioned in docs/testing.md"))
              unmentioned))))

(defn extract-qa-none-line
  "Extract a 'QA: none' line from the feature description block. Returns the line
   as a string (e.g. 'QA: none - reason'), or nil if not found. Only matches in
   the description block (the lines between Feature: and the first Scenario)."
  [feature-text]
  (let [desc (extract-feature-description feature-text)
        lines (str/split desc #"\n")
        qa-line (some #(when (re-matches #"QA:\s*none.*" (str/trim %))
                         (str/trim %))
                      lines)]
    (when qa-line
      ;; Normalize whitespace: collapse multiple spaces to single spaces
      (str/replace qa-line #"\s+" " "))))

(defn- has-qa-procedure?
  "True if the procedure file exists."
  [procedure-paths slug]
  (contains? procedure-paths (str "specs/qa/" slug ".edn")))

(defn- qa-none-has-reason?
  "True if the QA: none line has a non-empty reason after the dash."
  [qa-none-line]
  (let [after-dash (str/trim (str/replace-first qa-none-line #"QA:\s*none\s*-?\s*" ""))]
    (not (str/blank? after-dash))))

(defn qa-findings
  "Check added features for QA procedures or opt-out lines with reasons.
   added-features is a sequence of {:path ... :text ...} maps.
   procedure-paths is a set of procedure file paths that exist.
   Returns vector of finding strings."
  [added-features procedure-paths]
  (vec (mapcat (fn [{:keys [path text]}]
                 (let [slug (-> path (str/replace #".feature$" "") (str/replace #"^specs/features/" ""))
                       has-proc? (has-qa-procedure? procedure-paths slug)
                       qa-none-line (extract-qa-none-line text)]
                   (cond
                     has-proc? []
                     qa-none-line (if (qa-none-has-reason? qa-none-line)
                                    []
                                    [(str "feature \"" slug "\" has a \"QA: none\" opt-out with no reason")])
                     :else [(str "feature \"" slug "\" has no QA procedure and no \"QA: none\" line")])))
               added-features)))

(defn run
  "Orchestrate the check: read bb.edn and docs/testing.md from disk, get added
   features and procedure paths from arguments (shell wrapper's job), print findings
   to stdout, return exit code (0 = pass/advisory only, 1 = findings exist)."
  [bb-edn-text testing-md-text added-features procedure-paths]
  (let [task-finding-list (task-findings bb-edn-text testing-md-text)
        qa-finding-list (qa-findings added-features procedure-paths)
        all-findings (concat task-finding-list qa-finding-list)]
    (if (empty? all-findings)
      (do (println "  PASS  no unmentioned tasks or orphaned features")
          0)
      (do (println (str "  ADVISORY  " (count all-findings) " finding(s):"))
          (doseq [finding all-findings]
            (println (str "    " finding)))
          0))))
