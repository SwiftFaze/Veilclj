(ns veil.acceptance.steps.qa
  "Acceptance steps for deterministic keyboard QA."
  (:require [veil.acceptance.step-support :refer [ok check fail]]
            [veil.game.state :as state]
            [veil.ui.input :as input]
            [veil.ui.qa.script :as script]
            [veil.ui.qa.play :as play]
            [veil.ui.qa.log :as log]
            [veil.ui.qa.launch :as launch]
            [veil.ui.qa.procedure :as procedure]))

(defn- simple-handler [s ev]
  (state/handle-input s (input/event->input ev)))

(defn- expand-script [script-text]
  "Expand a script from table cell format (lines separated by ' / ') to newline-separated form."
  (let [lines (clojure.string/split script-text #" / ")
        trimmed-lines (mapv clojure.string/trim lines)]
    (clojure.string/join "\n" trimmed-lines)))

(defn- play-script [world script-text]
  "Parse and play a script, storing state, entries, log text, and finished flag."
  (let [script-text-expanded (expand-script script-text)
        parse-result (script/parse script-text-expanded)]
    (if (:error parse-result)
      (fail (:error parse-result))
      (let [steps (:steps parse-result)
            play-result (play/play simple-handler (or (:state @world) (state/initial)) steps)
            entries (:entries play-result)
            rendered-text (binding [*print-namespace-maps* false]
                            (str (pr-str (log/header)) "\n" (log/render entries)))]
        (swap! world assoc
               :state (:state play-result)
               :entries entries
               :log-text rendered-text
               :finished? (:finished? play-result))
        (ok)))))

(def handlers
  [[#"the key script line \"(.*)\" is parsed"
    (fn [world [_ line]]
      (let [result (script/parse line)]
        (swap! world assoc :script-result result)
        (ok)))]

   [#"the key script \"(.*)\" is parsed"
    (fn [world [_ script-text]]
      (let [script-text-expanded (expand-script script-text)
            result (script/parse script-text-expanded)]
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
                               (clojure.string/split keys-text #" "))
                actual-keys (vec key-names)]
            (check (= (vec expected-keys) actual-keys)
                   (str "expected " (vec expected-keys) " got " actual-keys))))))]

   [#"the keys are pressed on ticks ([0-9 ]+)"
    (fn [world [_ ticks-text]]
      (let [result (:script-result @world)
            steps (:steps result)
            expected-ticks (mapv #(Long/parseLong %)
                                 (clojure.string/split ticks-text #" "))
            actual-ticks (mapv :tick steps)]
        (check (= expected-ticks actual-ticks)
               (str "expected ticks " expected-ticks " got " actual-ticks))))]

   [#"the script is rejected with \"(.*)\""
    (fn [world [_ error-msg]]
      (let [result (:script-result @world)]
        (if-not (:error result)
          (fail "script was not rejected")
          (check (= error-msg (:error result))
                 (str "expected error '" error-msg "' but got '" (:error result) "'")))))]

   [#"the game input is (.+)"
    (fn [world [_ input-name]]
      (let [key-name (:pressed-key @world)]
        (if-not key-name
          (fail "no key was pressed")
          (let [event (script/key->event key-name)
                game-input (input/event->input event)
                expected-input (case input-name
                               "up" :up
                               "down" :down
                               "confirm" :confirm
                               "back" :back
                               "no input" nil
                               nil)]
            (check (= expected-input game-input)
                   (str "expected input " expected-input " got " game-input))))))]

   [#"the script \"(.*)\" is played$"
    (fn [world [_ script-text]]
      (play-script world script-text))]

   [#"the script \"(.*)\" has been played$"
    (fn [world [_ script-text]]
      (play-script world script-text))]

   [#"the run is finished"
    (fn [world _]
      (check (:finished? @world)
             "run is not finished"))]

   [#"the log entries are \"(.*)\""
    (fn [world [_ log-text]]
      (let [entries (:entries @world)
            expected-strs (clojure.string/split log-text #" / ")
            expected-entries (mapv clojure.edn/read-string expected-strs)
            actual-entries entries]
        (check (= expected-entries actual-entries)
               (str "expected " expected-entries " got " actual-entries))))]

   [#"the first log line is \{:log/version 1\}"
    (fn [world _]
      (let [log-text (:log-text @world)
            lines (clojure.string/split log-text #"\n")
            first-line (first lines)]
        (check (= "{:log/version 1}" first-line)
               (str "first line was: " first-line))))]

   [#"the log entries follow it"
    (fn [world _]
      (let [log-text (:log-text @world)
            lines (clojure.string/split log-text #"\n")
            non-blank-lines (filter (fn [l] (not (clojure.string/blank? l))) lines)]
        (check (> (count non-blank-lines) 1)
               "log has no entries after header")))]

   [#"the log text has ([0-9]+) lines"
    (fn [world [_ num-str]]
      (let [log-text (:log-text @world)
            lines (clojure.string/split log-text #"\n")
            expected-count (Long/parseLong num-str)
            actual-count (count (filter (fn [l] (not (clojure.string/blank? l))) lines))]
        (check (= expected-count actual-count)
               (str "expected " expected-count " lines, got " actual-count))))]

   [#"every log line reads back as one EDN map"
    (fn [world _]
      (let [log-text (:log-text @world)
            lines (clojure.string/split log-text #"\n")
            non-blank-lines (filter (fn [l] (not (clojure.string/blank? l))) lines)]
        (try
          (doseq [line non-blank-lines]
            (clojure.edn/read-string line))
          (ok)
          (catch Exception e
            (fail (str "EDN parse error: " (.getMessage e)))))))]

   [#"the launch arguments are \"(.*)\""
    (fn [world [_ args-text]]
      (let [args-list (if (clojure.string/blank? args-text)
                       []
                       (clojure.string/split args-text #" "))
            result (launch/parse-args args-list)]
        (swap! world assoc :launch result)
        (ok)))]

   [#"the key script is ([^ ]+)"
    (fn [world [_ file-or-none]]
      (let [launch-result (:launch @world)
            expected-keys (case file-or-none
                          "none" nil
                          file-or-none)
            actual-keys (:keys launch-result)]
        (check (= expected-keys actual-keys)
               (str "expected keys " expected-keys " got " actual-keys))))]

   [#"the log file is ([^ ]+)"
    (fn [world [_ file-or-none]]
      (let [launch-result (:launch @world)
            expected-log (case file-or-none
                         "none" nil
                         file-or-none)
            actual-log (:log launch-result)]
        (check (= expected-log actual-log)
               (str "expected log " expected-log " got " actual-log))))]

   [#"no script is played and no log is written"
    (fn [world _]
      (let [launch-result (:launch @world)]
        (check (and (nil? (:keys launch-result)) (nil? (:log launch-result)))
               (str "expected both nil, got keys: " (:keys launch-result) ", log: " (:log launch-result)))))]

   [#"the launch is rejected with \"(.*)\""
    (fn [world [_ error-msg]]
      (let [result (:launch @world)]
        (check (:error result)
               "launch was not rejected")
        (check (= error-msg (:error result))
               (str "expected error '" error-msg "' but got '" (:error result) "'"))))]

   [#"the procedure expects \"(.*)\""
    (fn [world [_ expected-text]]
      (if (clojure.string/blank? expected-text)
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
                      expected-list (mapv clojure.edn/read-string (clojure.string/split expected-text #" / "))
                      check-result (procedure/check entries expected-list)]
                  (swap! world assoc :report (:report check-result) :status (:status check-result))
                  (ok))))))))]

   [#"the report marks the expected entries as ([a-z ]+)"
    (fn [world [_ report-text]]
      (let [report (:report @world)
            expected-report (mapv keyword (clojure.string/split report-text #" "))
            actual-report report]
        (check (= expected-report actual-report)
               (str "expected report " expected-report " got " actual-report))))]

   [#"the QA run exits with status ([0-9])"
    (fn [world [_ status-str]]
      (let [expected-status (Long/parseLong status-str)
            actual-status (:status @world)]
        (check (= expected-status actual-status)
               (str "expected status " expected-status " got " actual-status))))]

   [#"the check is rejected with \"(.*)\""
    (fn [world [_ error-msg]]
      (check (= error-msg (:error @world))
             (str "expected error '" error-msg "' but got '" (:error @world) "'")))]

   [#"the procedure is rejected with \"(.*)\""
    (fn [world [_ error-msg]]
      (check (= error-msg (:error @world))
             (str "expected error '" error-msg "' but got '" (:error @world) "'")))]

   [#"the log text is \"(.*)\""
    (fn [world [_ log-content]]
      (let [log-content-expanded (clojure.string/replace log-content " / " "\n")]
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

   [#"the QA run is rejected with \"(.*)\""
    (fn [world [_ error-msg]]
      (check (= error-msg (:error @world))
             (str "expected error '" error-msg "' but got '" (:error @world) "'")))]])
