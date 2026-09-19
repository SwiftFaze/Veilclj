(ns veil.ui.qa.log
  "Log format: version line + EDN maps, one per line."
  (:require [clojure.edn :as edn]
            [clojure.string :as str]))

(def ^:private no-version-error "log has no version line")

(defn header
  "Return the log header map."
  []
  {:log/version 1})

(defn render
  "Render entries as pr-str, one per line, newline-terminated."
  [entries]
  (binding [*print-namespace-maps* false]
    (str (str/join "\n" (map pr-str entries)) "\n")))

(defn- read-header
  "The header map from the log's first line, or nil if it isn't one."
  [line]
  (try
    (let [header (edn/read-string line)]
      (when (map? header) header))
    (catch Exception _ nil)))

(defn parse
  "Parse log text into {:entries [...]} or {:error \"...\"}."
  [text]
  (let [lines (remove str/blank? (str/split text #"\r?\n"))
        version (:log/version (some-> (first lines) read-header))]
    (cond
      (nil? version)
      {:error no-version-error}

      (not= version 1)
      {:error (str "unsupported log version " version " (this runner reads 1)")}

      :else
      {:entries (map edn/read-string (drop 1 lines))})))
