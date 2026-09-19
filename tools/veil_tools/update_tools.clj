(ns veil-tools.update-tools
  "Moves every pinned Uncle Bob tool to the HEAD of its default branch.
  tools.deps git deps must name an exact sha (there is no :latest), so this
  finds each github.com/unclebob URL in the files below, asks the remote for
  its HEAD, and rewrites only the sha string next to it - formatting and
  comments are left alone."
  (:require [babashka.process :refer [sh]]
            [clojure.string :as str]))

(def pin-files ["bb.edn" "deps.edn" "tools/veil_tools/aps.clj"])

;; A URL followed by its sha: `:git/url "..." :git/sha "..."` in the edn files,
;; `(def url "...") (def sha "...")` in aps.clj.
(def pin-pattern
  #"\"(https://github\.com/unclebob/[^\"]+)\"(\)?\s*(?:\(def sha|:git/sha)\s*)\"([0-9a-f]{40})\"")

(defn pins [text]
  (for [[_ url _ sha] (re-seq pin-pattern text)]
    {:url url :sha sha}))

(defn repin [text heads]
  (str/replace text pin-pattern
               (fn [[_ url between sha]]
                 (str "\"" url "\"" between "\"" (get heads url sha) "\""))))

(defn- remote-head [url]
  (let [{:keys [exit out err]} (sh "git" "ls-remote" url "HEAD")]
    (when-not (zero? exit)
      (throw (ex-info (str "git ls-remote failed for " url ": " err) {:url url})))
    (first (str/split out #"\s"))))

(defn- short-sha [sha] (subs sha 0 7))

(defn- report [url old new]
  (if (= old new)
    (println (str "  latest  " url))
    (println (str "  behind  " url "  " (short-sha old) " -> " (short-sha new)
                  "\n          " url "/compare/" old "..." new))))

(defn run
  "Returns an exit code. Default: rewrite stale shas, 0. With --check: change
  nothing, 1 if anything is behind."
  [& args]
  (let [check? (some #{"--check"} args)
        texts  (into {} (map (juxt identity slurp)) pin-files)
        pinned (distinct (mapcat pins (vals texts)))
        heads  (into {} (map (juxt identity remote-head)) (distinct (map :url pinned)))
        stale  (remove #(= (:sha %) (heads (:url %))) pinned)]
    (run! #(report (:url %) (:sha %) (heads (:url %))) pinned)
    (cond
      (empty? stale) (do (println "All Uncle Bob tools are at their latest commit.") 0)
      check?         (do (println (str (count stale) " pin(s) behind. Run bb update-tools.")) 1)
      :else
      (do (doseq [[file text] texts
                  :let [updated (repin text heads)]
                  :when (not= text updated)]
            (spit file updated)
            (println (str "  updated " file)))
          (println "Run bash .claude/tools/check-clean.sh and bb mutate on changed files:"
                   "new tool versions can add findings on unchanged code.")
          0))))
