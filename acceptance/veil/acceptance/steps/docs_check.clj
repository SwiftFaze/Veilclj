(ns veil.acceptance.steps.docs-check
  "Acceptance steps for the advisory docs check."
  (:require [clojure.string :as str]
            [veil-tools.docs-check :as docs-check]
            [veil.acceptance.step-support :refer [ok check fail]]))

(defn- parse-task-list [text]
  "Parse comma-separated task names from text like 'play', 'spec' and 'qa'"
  (let [parts (str/split text #",\s*|\s+and\s+")]
    (mapv #(str/replace (str/trim %) #"[\"']" "") parts)))

(defn- parse-key-list [text]
  "Parse comma-separated keys from text like ':init', ':requires' and 'play'"
  (let [parts (str/split text #",\s*|\s+and\s+")]
    (mapv #(str/replace (str/trim %) #"[\"']" "") parts)))

(def ^:private step-handlers
  [[#"bb\.edn defines the tasks? \"(.+)\""
    (fn [world [_ tasks-text]]
      (let [task-names (parse-task-list tasks-text)
            bb-edn-text (str "{:tasks {" (str/join " " (map #(str (symbol %) " {}") task-names)) "}}")
            ]
        (swap! world assoc :bb-edn-text bb-edn-text)
        (ok)))]

   [#"bb\.edn's tasks map has the keys \"(.+)\""
    (fn [world [_ keys-text]]
      (let [keys (parse-key-list keys-text)
            bb-edn-text (str "{:tasks {" (str/join " " (map #(str (symbol %) " {}") keys)) "}}")
            ]
        (swap! world assoc :bb-edn-text bb-edn-text)
        (ok)))]

   [#"docs/testing\.md mentions?(.*)(?:\s+only)?"
    (fn [world [_ mentions-text]]
      (let [mentions (str/split (str/trim mentions-text) #",\s*|\s+and\s+")
            md-lines (mapv #(str/replace (str/trim %) #"[\"<>]" "") mentions)
            md-text (str/join "\n" md-lines)]
        (swap! world assoc :testing-md-text md-text)
        (ok)))]

   [#"docs/testing\.md contains the line \"(.+)\""
    (fn [world [_ line-text]]
      (let [current-md (or (:testing-md-text @world) "")
            updated-md (if (str/blank? current-md)
                         line-text
                         (str current-md "\n" line-text))]
        (swap! world assoc :testing-md-text updated-md)
        (ok)))]

   [#"the branch adds the feature file \"(.+)\""
    (fn [world [_ path]]
      ;; Initialize a basic feature structure if not already set
      (when-not (:feature-text @world)
        (let [slug (-> path (str/replace #".feature$" "") (str/replace #"^specs/features/" ""))]
          (swap! world assoc :feature-text (str "Feature: " slug "\n"))))
      ;; Add to added-features list with current feature-text
      (swap! world update :added-features
             (fn [features]
               (vec (conj (or features []) {:path path :text (or (:feature-text @world) "")}))))
      (ok))]

   [#"the procedure \"([^\"]+)\" exists"
    (fn [world [_ path]]
      (swap! world update :procedures
             (fn [procs]
               (set (conj (or procs #{}) path))))
      (ok))]

   [#"no (?:key script|procedure) \"([^\"]+)\" exists"
    (fn [world [_ path]]
      ;; Procedures are implicitly tracked; we just don't add this one
      (ok))]

   [#"its Feature: description block has the line \"(.+)\""
    (fn [world [_ line]]
      (let [current-text (or (:feature-text @world) "Feature: test\n")]
        ;; Ensure there's a Scenario line for the description block to work
        (let [with-scenario (if (str/includes? current-text "Scenario:")
                             current-text
                             (str current-text "\nScenario: test"))
              with-line (str/replace-first with-scenario
                                           #"(Feature:[^\n]*\n)"
                                           (str "$1  " line "\n"))]
          (swap! world assoc :feature-text with-line)))
      (ok))]

   [#"its Feature: description block has no \"QA: none\" line"
    (fn [world _]
      ;; Ensure feature text is set but has no QA: none line
      (swap! world assoc :feature-text (or (:feature-text @world) "Feature: test\nScenario: test\n"))
      (ok))]

   [#"the line \"(.*)\" appears only after its first Scenario"
    (fn [world [_ line]]
      (let [current-text (or (:feature-text @world) "Feature: test\n")
            ;; Ensure there's a Scenario line if not already present
            with-scenario (if (str/includes? current-text "Scenario:")
                            current-text
                            (str current-text "\nScenario: first"))
            ;; Append the line AFTER the Scenario line (not in the description block)
            with-line (str/replace-first with-scenario
                                         #"(Scenario:[^\n]*\n)"
                                         (str "$1  " line "\n"))]
        (swap! world assoc :feature-text with-line))
      (ok))]

   [#"the feature file \"([^\"]+)\" already exists on develop"
    (fn [world [_ path]]
      (swap! world update :existing-features
             (fn [existing]
               (vec (conj (or existing []) path))))
      (ok))]

   [#"the branch changes it"
    (fn [world _]
      (ok))]

   [#"the docs check runs"
    (fn [world _]
      (let [bb-edn-text (or (:bb-edn-text @world) "{:tasks {}}")
            testing-md-text (or (:testing-md-text @world) "")
            feature-text (or (:feature-text @world) "")
            added-features (or (:added-features @world) [])
            procedures (or (:procedures @world) #{})
            existing (or (:existing-features @world) [])
            ;; Update the last added feature with the current feature-text
            updated-added (if (and (seq added-features) (seq feature-text))
                            (assoc (vec added-features)
                                   (dec (count added-features))
                                   (assoc (last added-features) :text feature-text))
                            added-features)
            filtered-added (vec (filter (fn [{:keys [path]}]
                                          (not (some #(str/starts-with? path %) existing)))
                                        updated-added))]
        (swap! world assoc
               :findings (docs-check/qa-findings filtered-added procedures)
               :task-findings (docs-check/task-findings bb-edn-text testing-md-text)))
      (ok))]

   [#"it reports no findings"
    (fn [world _]
      (let [findings (vec (concat (:task-findings @world) (:findings @world)))]
        (check (empty? findings)
               (str "expected no findings, got: " findings))))]

   [#"it reports (\d+) finding(?:s)?"
    (fn [world [_ count-str]]
      (let [findings (concat (:task-findings @world) (:findings @world))
            expected-count (Long/parseLong count-str)]
        (check (= expected-count (count findings))
               (str "expected " expected-count " findings, got " (count findings)))))]

   [#"the finding says task \"([^\"]*)\" is not mentioned in docs/testing\.md"
    (fn [world [_ task-name]]
      (let [findings (:task-findings @world)
            expected (str "task \"" task-name "\" is not mentioned in docs/testing.md")]
        (check (some #(= % expected) findings)
               (str "expected finding '" expected "', got: " findings))))]

   [#"it reports findings for the tasks? \"(.*)\", in that order"
    (fn [world [_ tasks-text]]
      (let [task-names (parse-task-list tasks-text)
            findings (:task-findings @world)
            expected (vec (map #(str "task \"" % "\" is not mentioned in docs/testing.md")
                               task-names))]
        (check (= expected findings)
               (str "expected " expected ", got " findings))))]

   [#"task \"([^\"]*)\" is (mentioned|not mentioned)"
    (fn [world [_ task-name verdict-text]]
      (let [findings (:task-findings @world)
            finding-str (str "task \"" task-name "\" is not mentioned in docs/testing.md")
            is-mentioned (not (some #(= % finding-str) findings))
            expected-mentioned (= "mentioned" verdict-text)]
        (check (= expected-mentioned is-mentioned)
               (str "task \"" task-name "\" is " (if is-mentioned "mentioned" "not mentioned")
                    " but verdict is " verdict-text))))]

   [#"the finding says feature \"([^\"]*)\" has no QA procedure and no \"QA: none\" line"
    (fn [world [_ slug]]
      (let [findings (:findings @world)
            expected (str "feature \"" slug "\" has no QA procedure and no \"QA: none\" line")]
        (check (some #(= % expected) findings)
               (str "expected finding '" expected "', got: " findings))))]

   [#"the finding says feature \"([^\"]*)\" has a \"QA: none\" opt-out with no reason"
    (fn [world [_ slug]]
      (let [findings (:findings @world)
            expected (str "feature \"" slug "\" has a \"QA: none\" opt-out with no reason")]
        (check (some #(= % expected) findings)
               (str "expected finding '" expected "', got: " findings))))]])

(def handlers step-handlers)
