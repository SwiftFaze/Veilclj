(ns veil.ui.qa.procedure
  "QA procedures: check logs against expected entries."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(defn parse
  "Parse procedure EDN into {:script path :expect [...]} or {:error \"...\"}."
  [edn-text]
  (try
    (let [data (edn/read-string edn-text)]
      (cond
        (not (contains? data :script))
        {:error "missing :script"}

        (not (contains? data :expect))
        {:error "missing :expect"}

        (empty? (:expect data))
        {:error "expects nothing"}

        :else
        {:script (:script data) :expect (:expect data)}))
    (catch Exception _
      {:error "parse error"})))

(defn- matches?
  "True when every key/value in expected appears in the log entry."
  [expected entry]
  (every? (fn [[k v]] (= (get entry k) v)) expected))

(defn- next-report
  "Look for expected in the log from position pos on. Returns the new position
   (just after the match) and the report extended with :seen or :missing."
  [log-entries {:keys [pos report]} expected]
  (let [found (first (keep-indexed (fn [idx entry] (when (matches? expected entry) idx))
                                   (drop pos log-entries)))]
    (if found
      {:pos (+ pos found 1) :report (conj report :seen)}
      {:pos pos :report (conj report :missing)})))

(defn check
  "Check log entries against expected list, returning
   {:report [:seen|:missing ...] :status 0|1}."
  [log-entries expected]
  (let [report (:report (reduce (partial next-report log-entries)
                                {:pos 0 :report []}
                                expected))]
    {:report report :status (if (some #{:missing} report) 1 0)}))

(defn path
  "Return the expected path for a QA slug."
  [slug]
  (str "specs/qa/" slug ".edn"))

(defn log-path
  "Return the path the game writes a QA slug's log to."
  [slug]
  (str "target/qa/" slug ".log.edn"))

(defn unknown-error
  "Return the error message for an unknown slug."
  [slug]
  (str "no QA procedure for " slug " (expected " (path slug) ")"))

(defn report-lines
  "Generate report lines for a check result.
   Returns a vector of strings, one per expected entry."
  [expected report]
  (binding [*print-namespace-maps* false]
    (mapv (fn [exp status]
            (str (if (= status :seen) "seen    " "MISSING") " " (pr-str exp)))
          expected
          report)))

(defn verdict
  "The output of one finished procedure: its report lines then the final
   PASS/FAIL line. Returns {:out [lines] :err [] :status 0|1}."
  [slug expected {:keys [report status]}]
  (let [missing (count (filter #{:missing} report))
        final (if (zero? status)
                (str "QA " slug ": PASS")
                (str "QA " slug ": FAIL (" missing " missing)"))]
    {:out (conj (report-lines expected report) final)
     :err []
     :status status}))

(defn child-error
  "The error message for a game process that did not finish cleanly, or nil.
   child is {:timed-out? true} or {:exit code}."
  [{:keys [timed-out? exit]}]
  (cond
    timed-out? "game did not finish within 60s"
    (not (zero? exit)) (str "game exited with code " exit)))

(defn slugs
  "Extract slugs from a list of file names.
   Returns {:slugs [sorted list]} or {:error \"no QA procedures in specs/qa\"}."
  [file-names]
  (let [edn-files (filter #(.endsWith % ".edn") file-names)
        slugs-list (sort (map #(subs % 0 (- (count %) 4)) edn-files))]
    (if (empty? slugs-list)
      {:error "no QA procedures in specs/qa"}
      {:slugs slugs-list})))

(defn summary
  "Generate summary line for multiple procedure results.
   Takes a sequence of [slug status] pairs.
   Returns {:line \"QA all: p passed, f failed...\" :status 0|1}."
  [results]
  (let [failed-slugs (map first (remove (comp zero? second) results))
        failed (count failed-slugs)
        passed (- (count results) failed)
        failed-str (when (pos? failed) (str " (" (str/join ", " failed-slugs) ")"))]
    {:line (str "QA all: " passed " passed, " failed " failed" failed-str)
     :status (if (pos? failed) 1 0)}))
