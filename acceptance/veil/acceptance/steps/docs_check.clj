(ns veil.acceptance.steps.docs-check
  "Acceptance steps for the advisory docs check."
  (:require [clojure.string :as str]
            [veil-tools.docs-check :as docs-check]
            [veil.acceptance.step-support :refer [ok check]]))

(defn- split-list
  "Split step text like `\"play\", \"spec\" and \"qa\"` into bare items."
  [text]
  (mapv #(str/replace (str/trim %) #"[\"'<>]" "")
        (str/split (str/trim text) #",\s*|\s+and\s+")))

(defn- bb-edn-text
  "A bb.edn whose tasks map has one empty entry per key."
  [task-keys]
  (str "{:tasks {" (str/join " " (map #(str % " {}") task-keys)) "}}"))

(defn- arrange
  "Store the given world entries; the step itself always succeeds."
  [world & entries]
  (apply swap! world assoc entries)
  (ok))

(defn- add-feature [features path]
  (conj (or features []) {:path path :description [] :after-scenario []}))

(defn- update-last-feature
  "Apply f to the feature the most recent step added."
  [features f & args]
  (apply update features (dec (count features)) f args))

(defn- indented [lines]
  (map #(str "  " %) lines))

(defn- feature-text
  "The feature file's text: description lines under Feature:, then the first
   Scenario, then any lines that belong after it."
  [{:keys [path description after-scenario]}]
  (let [slug (-> path (str/replace #"\.feature$" "") (str/replace #"^specs/features/" ""))]
    (str/join "\n" (concat [(str "Feature: " slug)]
                           (indented description)
                           ["Scenario: first"]
                           (indented after-scenario)))))

(defn- all-findings [world]
  (concat (:task-findings @world) (:qa-findings @world)))

(defn- has-finding [world expected]
  (let [findings (all-findings world)]
    (check (contains? (set findings) expected)
           (str "expected finding '" expected "', got: " findings))))

(defn- task-finding [task-name]
  (str "task \"" task-name "\" is not mentioned in docs/testing.md"))

;; The check is only ever handed features the branch added (the shell wrapper
;; works that out from git), so there is nothing to arrange for a feature that
;; already exists on develop, or for a procedure or key script that is absent.
(defn- nothing-to-arrange [_world _match]
  (ok))

(def ^:private step-handlers
  [[#"bb\.edn defines the tasks? \"(.+)\""
    (fn [world [_ tasks-text]]
      (arrange world :bb-edn-text (bb-edn-text (split-list tasks-text))))]

   [#"bb\.edn's tasks map has the keys \"(.+)\""
    (fn [world [_ keys-text]]
      (arrange world :bb-edn-text (bb-edn-text (split-list keys-text))))]

   [#"docs/testing\.md mentions? (.*?)(?:\s+only)?"
    (fn [world [_ mentions-text]]
      (arrange world :testing-md-text (str/join "\n" (split-list mentions-text))))]

   [#"docs/testing\.md contains the line \"(.+)\""
    (fn [world [_ line-text]]
      (swap! world update :testing-md-text #(str/join "\n" (remove str/blank? [% line-text])))
      (ok))]

   [#"the branch adds the feature file \"(.+)\""
    (fn [world [_ path]]
      (swap! world update :added-features add-feature path)
      (ok))]

   [#"the procedure \"([^\"]+)\" exists"
    (fn [world [_ path]]
      (swap! world update :procedures (fnil conj #{}) path)
      (ok))]

   [#"no (?:key script|procedure) \"([^\"]+)\" exists"
    nothing-to-arrange]

   [#"its Feature: description block has the line \"(.+)\""
    (fn [world [_ line]]
      (swap! world update :added-features update-last-feature update :description conj line)
      (ok))]

   [#"its Feature: description block has no \"QA: none\" line"
    nothing-to-arrange]

   [#"the line \"(.*)\" appears only after its first Scenario"
    (fn [world [_ line]]
      (swap! world update :added-features update-last-feature update :after-scenario conj line)
      (ok))]

   [#"the feature file \"([^\"]+)\" already exists on develop"
    nothing-to-arrange]

   [#"the branch changes it"
    nothing-to-arrange]

   [#"the docs check runs"
    (fn [world _]
      (let [{:keys [bb-edn-text testing-md-text added-features procedures]}
            (merge {:bb-edn-text "{:tasks {}}" :testing-md-text ""
                    :added-features [] :procedures #{}}
                   @world)]
        (arrange world
                 :task-findings (docs-check/task-findings bb-edn-text testing-md-text)
                 :qa-findings (docs-check/qa-findings
                               (map #(assoc % :text (feature-text %)) added-features)
                               procedures))))]

   [#"it reports no findings"
    (fn [world _]
      (let [findings (all-findings world)]
        (check (empty? findings)
               (str "expected no findings, got: " findings))))]

   [#"it reports (\d+) finding(?:s)?"
    (fn [world [_ count-str]]
      (let [expected-count (Long/parseLong count-str)
            findings (all-findings world)]
        (check (= expected-count (count findings))
               (str "expected " expected-count " findings, got " (count findings)))))]

   [#"the finding says task \"([^\"]*)\" is not mentioned in docs/testing\.md"
    (fn [world [_ task-name]]
      (has-finding world (task-finding task-name)))]

   [#"it reports findings for the tasks? \"(.*)\", in that order"
    (fn [world [_ tasks-text]]
      (let [expected (mapv task-finding (split-list tasks-text))
            findings (vec (all-findings world))]
        (check (= expected findings)
               (str "expected " expected ", got " findings))))]

   [#"task \"([^\"]*)\" is (mentioned|not mentioned)"
    (fn [world [_ task-name verdict-text]]
      (let [is-mentioned (not (contains? (set (all-findings world)) (task-finding task-name)))]
        (check (= (= "mentioned" verdict-text) is-mentioned)
               (str "task \"" task-name "\" is " (if is-mentioned "mentioned" "not mentioned")
                    " but verdict is " verdict-text))))]

   [#"the finding says feature \"([^\"]*)\" has no QA procedure and no \"QA: none\" line"
    (fn [world [_ slug]]
      (has-finding world (str "feature \"" slug "\" has no QA procedure and no \"QA: none\" line")))]

   [#"the finding says feature \"([^\"]*)\" has a \"QA: none\" opt-out with no reason"
    (fn [world [_ slug]]
      (has-finding world (str "feature \"" slug "\" has a \"QA: none\" opt-out with no reason")))]])

(def handlers step-handlers)
