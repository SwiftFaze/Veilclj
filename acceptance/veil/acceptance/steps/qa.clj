(ns veil.acceptance.steps.qa
  "Acceptance steps for deterministic keyboard QA."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [veil.acceptance.step-support :refer [ok check fail]]
            [veil.game.state :as state]
            [veil.ui.input :as input]
            [veil.ui.qa.script :as script]
            [veil.ui.qa.play :as play]
            [veil.ui.qa.log :as log]
            [veil.ui.qa.launch :as launch]
            [veil.ui.qa.procedure :as procedure]))

(defn- simple-handler [s ev]
  (state/handle-input s (input/event->input ev)))

(defn- expand-script
  "Expand a script from table cell format (lines separated by ' / ') to newline-separated form."
  [script-text]
  (str/join "\n" (map str/trim (str/split script-text #" / "))))

(defn- error-is
  "Check that actual is the expected error message."
  [expected actual]
  (check (= expected actual)
         (str "expected error '" expected "' but got '" actual "'")))

(def ^:private input-names
  {"up" :up "down" :down "confirm" :confirm "back" :back "no input" nil})

(defn- script-key?
  "True when name is one key line the script parser accepts."
  [name]
  (= {:steps [{:tick 1 :key name}]} (script/parse name)))

(defn- launched-option
  "Check one option of the parsed launch arguments; a rejected launch fails
   instead of reading as \"none\"."
  [world option label expected-text]
  (let [launch-result (:launch @world)
        expected (when-not (= "none" expected-text) expected-text)]
    (if (:error launch-result)
      (fail (str "launch was rejected: " (:error launch-result)))
      (check (= expected (get launch-result option))
             (str "expected " label " " expected " got " (get launch-result option))))))

(defn- play-script
  "Parse and play a script, storing state, entries, log text, and finished flag."
  [world script-text]
  (let [parse-result (script/parse (expand-script script-text))]
    (if (:error parse-result)
      (fail (:error parse-result))
      (let [play-result (play/play simple-handler (or (:state @world) (state/initial))
                                   (:steps parse-result))
            entries (:entries play-result)]
        (swap! world assoc
               :state (:state play-result)
               :entries entries
               :log-text (log/render (cons (log/header) entries))
               :finished? (:finished? play-result))
        (ok)))))

(defn- failing-on-error
  "Wrap a step handler so a malformed table cell (an unreadable EDN map, a
   non-number where one is expected) fails the step instead of crashing it."
  [[pattern handler]]
  [pattern (fn [world match]
             (try
               (handler world match)
               (catch Exception e
                 (fail (str "step could not read its input: " (.getMessage e))))))])

(def ^:private step-handlers
  [[#"the key script line \"(.*)\" is parsed"
    (fn [world [_ line]]
      (let [result (script/parse line)]
        (swap! world assoc :script-result result)
        (ok)))]

   [#"the key script \"(.*)\" is parsed"
    (fn [world [_ script-text]]
      (let [result (script/parse (expand-script script-text))]
        (swap! world assoc :script-result result)
        (ok)))]

   [#"the script key (\S+) is pressed"
    (fn [world [_ key-name]]
      (swap! world assoc :pressed-key key-name)
      (ok))]

   [#"the script presses ([^ ].*)"
    (fn [world [_ keys-text]]
      (let [result (:script-result @world)]
        (if (:error result)
          (fail (str "script was rejected: " (:error result)))
          (let [steps (:steps result)
                key-names (map :key steps)
                expected-keys (if (= keys-text "no keys")
                               []
                               (str/split keys-text #" "))
                actual-keys (vec key-names)]
            (check (= (vec expected-keys) actual-keys)
                   (str "expected " (vec expected-keys) " got " actual-keys))))))]

   [#"the keys are pressed on ticks ([0-9 ]+)"
    (fn [world [_ ticks-text]]
      (let [result (:script-result @world)
            steps (:steps result)
            expected-ticks (mapv #(Long/parseLong %)
                                 (str/split ticks-text #" "))
            actual-ticks (mapv :tick steps)]
        (check (= expected-ticks actual-ticks)
               (str "expected ticks " expected-ticks " got " actual-ticks))))]

   [#"the script is rejected with \"(.*)\""
    (fn [world [_ error-msg]]
      (let [result (:script-result @world)]
        (if-not (:error result)
          (fail "script was not rejected")
          (error-is error-msg (:error result)))))]

   [#"the game input is (.+)"
    (fn [world [_ input-name]]
      (let [key-name (:pressed-key @world)]
        (cond
          (not key-name) (fail "no key was pressed")
          (not (script-key? key-name)) (fail (str "not a script key: " key-name))
          (not (contains? input-names input-name)) (fail (str "unknown input: " input-name))
          :else (let [game-input (input/event->input (script/key->event key-name))
                      expected-input (input-names input-name)]
                  (check (= expected-input game-input)
                         (str "expected input " expected-input " got " game-input))))))]

   [#"the script \"(.*)\" (?:is|has been) played$"
    (fn [world [_ script-text]]
      (play-script world script-text))]

   [#"the run is finished"
    (fn [world _]
      (check (:finished? @world)
             "run is not finished"))]

   [#"the log entries are \"(.*)\""
    (fn [world [_ log-text]]
      (let [entries (:entries @world)
            expected-strs (str/split log-text #" / ")
            expected-entries (mapv edn/read-string expected-strs)
            actual-entries entries]
        (check (= expected-entries actual-entries)
               (str "expected " expected-entries " got " actual-entries))))]

   [#"the first log line is \{:log/version 1\}"
    (fn [world _]
      (let [log-text (:log-text @world)
            lines (str/split log-text #"\n")
            first-line (first lines)]
        (check (= "{:log/version 1}" first-line)
               (str "first line was: " first-line))))]

   [#"the log entries follow it"
    (fn [world _]
      (let [log-text (:log-text @world)
            lines (str/split log-text #"\n")
            non-blank-lines (filter (fn [l] (not (str/blank? l))) lines)]
        (check (> (count non-blank-lines) 1)
               "log has no entries after header")))]

   [#"the log text has ([0-9]+) lines"
    (fn [world [_ num-str]]
      (let [log-text (:log-text @world)
            lines (str/split log-text #"\n")
            expected-count (Long/parseLong num-str)
            actual-count (count (filter (fn [l] (not (str/blank? l))) lines))]
        (check (= expected-count actual-count)
               (str "expected " expected-count " lines, got " actual-count))))]

   [#"every log line reads back as one EDN map"
    (fn [world _]
      (let [log-text (:log-text @world)
            lines (str/split log-text #"\n")
            non-blank-lines (filter (fn [l] (not (str/blank? l))) lines)]
        (try
          (doseq [line non-blank-lines]
            (edn/read-string line))
          (ok)
          (catch Exception e
            (fail (str "EDN parse error: " (.getMessage e)))))))]

   [#"the launch arguments are \"(.*)\""
    (fn [world [_ args-text]]
      (let [args-list (if (str/blank? args-text)
                       []
                       (str/split args-text #" "))
            result (launch/parse-args args-list)]
        (swap! world assoc :launch result)
        (ok)))]

   [#"the key script is ([^ ]+)"
    (fn [world [_ file-or-none]]
      (launched-option world :keys "keys" file-or-none))]

   [#"the log file is ([^ ]+)"
    (fn [world [_ file-or-none]]
      (launched-option world :log "log" file-or-none))]

   [#"no script is played and no log is written"
    (fn [world _]
      (let [launch-result (:launch @world)]
        (check (= {:keys nil :log nil} launch-result)
               (str "expected no script and no log, got " launch-result))))]

   [#"the launch is rejected with \"(.*)\""
    (fn [world [_ error-msg]]
      (let [result (:launch @world)]
        (if-not (:error result)
          (fail "launch was not rejected")
          (error-is error-msg (:error result)))))]

   [#"the procedure expects \"(.*)\""
    (fn [world [_ expected-text]]
      (if (str/blank? expected-text)
        (do
          (swap! world assoc :status 1 :error "expects nothing")
          (ok))
        (let [log-text (:log-text @world)]
          (if-not log-text
            (fail "no script has been played (log text not set)")
            (let [log-parse-result (log/parse log-text)]
              (if (:error log-parse-result)
                (do
                  (swap! world assoc :status 1 :error (:error log-parse-result))
                  (ok))
                (let [entries (:entries log-parse-result)
                      expected-list (mapv edn/read-string (str/split expected-text #" / "))
                      check-result (procedure/check entries expected-list)]
                  (swap! world assoc :report (:report check-result) :status (:status check-result))
                  (ok))))))))]

   [#"the report marks the expected entries as ([a-z ]+)"
    (fn [world [_ report-text]]
      (let [report (:report @world)
            expected-report (mapv keyword (str/split report-text #" "))
            actual-report report]
        (check (= expected-report actual-report)
               (str "expected report " expected-report " got " actual-report))))]

   [#"the QA run exits with status ([0-9])"
    (fn [world [_ status-str]]
      (let [expected-status (Long/parseLong status-str)
            actual-status (or (:status (:summary @world)) (:status @world))]
        (check (= expected-status actual-status)
               (str "expected status " expected-status " got " actual-status))))]

   [#"the (?:check|procedure|QA run) is rejected with \"(.*)\""
    (fn [world [_ error-msg]]
      (error-is error-msg (:error @world)))]

   [#"the log text is \"(.*)\""
    (fn [world [_ log-content]]
      (let [log-content-expanded (str/replace log-content " / " "\n")]
        (swap! world assoc :log-text log-content-expanded)
        (ok)))]

   [#"the procedure file reads \"(.*)\""
    (fn [world [_ edn-text]]
      (let [result (procedure/parse edn-text)]
        (if (:error result)
          (do
            (swap! world assoc :status 1 :error (:error result))
            (ok))
          (do
            (swap! world assoc :procedure result)
            (ok)))))]

   [#"the QA slug is \"(.*)\""
    (fn [world [_ slug]]
      (swap! world assoc :status 1 :error (procedure/unknown-error slug))
      (ok))]

   [#"the procedure files are \"(.*)\""
    (fn [world [_ files-text]]
      (let [file-names (if (str/blank? files-text)
                         []
                         (mapv str/trim (str/split files-text #" / ")))
            result (procedure/slugs file-names)]
        (if (:error result)
          (do
            (swap! world assoc :error (:error result) :status 1)
            (ok))
          (do
            (swap! world assoc :slugs-result result)
            (ok)))))]

   [#"the slugs to run are \"(.*)\""
    (fn [world [_ slugs-text]]
      (let [slugs-result (:slugs-result @world)]
        (if (:error slugs-result)
          (fail "procedure files were rejected")
          (let [expected-slugs (if (str/blank? slugs-text)
                                 []
                                 (str/split slugs-text #" "))
                actual-slugs (:slugs slugs-result)]
            (check (= (vec expected-slugs) actual-slugs)
                   (str "expected slugs " (vec expected-slugs) " got " (vec actual-slugs)))))))]

   [#"the procedures finish with \"(.*)\""
    (fn [world [_ results-text]]
      (let [results-list (if (str/blank? results-text)
                           []
                           (mapv (fn [item]
                                   (let [[slug status-str] (str/split (str/trim item) #":")]
                                     [slug (Long/parseLong status-str)]))
                                 (str/split results-text #" / ")))
            summary-result (procedure/summary results-list)]
        (swap! world assoc :summary summary-result)
        (ok)))]

   [#"the summary reads \"(.*)\""
    (fn [world [_ expected-line]]
      (let [summary-result (:summary @world)]
        (if-not summary-result
          (fail "no summary was computed")
          (check (= expected-line (:line summary-result))
                 (str "expected '" expected-line "' but got '" (:line summary-result) "'")))))]])

(def handlers (mapv failing-on-error step-handlers))
