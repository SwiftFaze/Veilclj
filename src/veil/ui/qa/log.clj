(ns veil.ui.qa.log
  "Log format: version line + EDN maps, one per line.")

(defn header
  "Return the log header map."
  []
  {:log/version 1})

(defn render
  "Render entries as pr-str, one per line, newline-terminated."
  [entries]
  (binding [*print-namespace-maps* false]
    (str (clojure.string/join "\n" (map pr-str entries)) "\n")))

(defn parse
  "Parse log text into {:entries [...]} or {:error \"...\"}."
  [text]
  (let [lines (clojure.string/split text #"\r?\n")
        non-empty-lines (filter (fn [line] (not (clojure.string/blank? line))) lines)]
    (cond
      (empty? non-empty-lines)
      {:error "log has no version line"}

      :else
      (let [first-line (first non-empty-lines)]
        (try
          (let [header-map (clojure.edn/read-string first-line)]
            (if (map? header-map)
              (let [version (:log/version header-map)]
                (if (nil? version)
                  {:error "log has no version line"}
                  (if (= version 1)
                    (let [rest-lines (rest non-empty-lines)
                          entries (map clojure.edn/read-string rest-lines)]
                      {:entries entries})
                    {:error (str "unsupported log version " version " (this runner reads 1)")})))
              {:error "log has no version line"}))
          (catch Exception _
            {:error "log has no version line"}))))))
