(ns veil-tools.single-answer-runner
  "SPIKE: the `bb single-answer` entry point. Reads the files, calls
  veil-tools.single-answer with the real network `ask`, prints. No decisions."
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str]
            [veil-tools.jev :as jev]
            [veil-tools.single-answer :as single-answer]))

(def ^:private default-threshold 0.30)

(defn- sources
  "path -> source text for every existing path."
  [paths]
  (into {} (for [path paths :when (fs/exists? path)] [path (slurp path)])))

(defn- clj-files [dir]
  (map (comp fs/unixify str) (fs/glob dir "**.clj")))

(defn- changed-ui-files
  "The veil.ui files this branch touched, which is what the gate would judge."
  [base]
  (->> (process/shell {:out :string :continue true}
                      "git" "diff" "--name-only" (str base "...HEAD"))
       :out
       str/split-lines
       (filter #(str/starts-with? % "src/veil/ui/"))
       (filter #(str/ends-with? % ".clj"))))

(defn- positional
  "args with every flag removed, and the value that follows a flag taking one,
  so `--threshold 0.4` can't be mistaken for a file called 0.4."
  [args]
  (loop [[arg & more] args acc []]
    (cond
      (nil? arg) acc
      (= "--threshold" arg) (recur (rest more) acc)
      (str/starts-with? arg "--") (recur more acc)
      :else (recur more (conj acc arg)))))

(defn- targets [args]
  (let [paths (positional args)]
    (cond
      (seq paths) paths
      (some #{"--all"} args) (clj-files "src/veil/ui")
      :else (changed-ui-files "origin/develop"))))

(defn- threshold [args]
  (if-let [value (second (drop-while #(not= "--threshold" %) args))]
    (parse-double value)
    default-threshold))

(defn run
  "Exit status is always 0: the check is advisory, and an advisory check that
  can fail a build is a blocking check with an apologetic name."
  [& args]
  (let [ui-files (targets args)
        api (single-answer/game-api (sources (clj-files "src/veil/game")))
        ui-fns (single-answer/ui-functions (sources ui-files))]
    (if (empty? ui-fns)
      (println "Single answer (advisory): no veil.ui functions in scope.")
      (let [{:keys [findings errors usage]}
            (single-answer/check jev/ask ui-fns api (threshold args))]
        (run! println (single-answer/report findings))
        (when (seq errors)
          (println)
          (println "Errors:")
          (run! #(println " " %) errors))
        (println)
        (printf "%d function(s) judged against %d veil.game function(s); %d in, %d out tokens.%n"
                (count ui-fns) (count api)
                (:input_tokens usage) (:output_tokens usage))))
    ;; The bb task exits on this return value, and System/exit doesn't flush.
    (flush)
    0))
