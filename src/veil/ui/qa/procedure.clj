(ns veil.ui.qa.procedure
  "QA procedures: check logs against expected entries.")

(defn parse
  "Parse procedure EDN into {:script path :expect [...]} or {:error \"...\"}."
  [edn-text]
  (try
    (let [data (clojure.edn/read-string edn-text)]
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

(defn check
  "Check log entries against expected list, returning
   {:report [:seen|:missing ...] :status 0|1}."
  [log-entries expected]
  (loop [expected expected
         log-entries log-entries
         search-pos 0
         report []]
    (if (empty? expected)
      {:report report :status (if (some #{:missing} report) 1 0)}
      (let [exp (first expected)
            remaining-expected (rest expected)
            remaining-log (drop search-pos log-entries)
            found-idx (first
                       (keep-indexed
                        (fn [idx entry]
                          (when (every? (fn [[k v]] (= (get entry k) v)) exp)
                            idx))
                        remaining-log))]
        (if found-idx
          (recur remaining-expected
                 log-entries
                 (+ search-pos found-idx 1)
                 (conj report :seen))
          (recur remaining-expected
                 log-entries
                 search-pos
                 (conj report :missing)))))))

(defn path
  "Return the expected path for a QA slug."
  [slug]
  (str "specs/qa/" slug ".edn"))

(defn unknown-error
  "Return the error message for an unknown slug."
  [slug]
  (str "no QA procedure for " slug " (expected " (path slug) ")"))
