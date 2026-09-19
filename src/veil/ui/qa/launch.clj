(ns veil.ui.qa.launch
  "Parse command-line arguments for QA runs: --keys, --log."
  (:require [clojure.string :as str]))

(def ^:private flag->option {"--keys" :keys "--log" :log})

(defn- flag-path
  "The path following a flag, or nil when there is none or the next word is
   itself a flag."
  [tail]
  (let [path (first tail)]
    (when-not (or (nil? path) (str/starts-with? path "--"))
      path)))

(defn- consume
  "Consume one argument from remaining, returning {:result r :remaining more}
   or {:error \"...\"}."
  [result [arg & tail]]
  (if-let [option (flag->option arg)]
    (if-let [path (flag-path tail)]
      {:result (assoc result option path) :remaining (drop 1 tail)}
      {:error (str arg " needs a file path")})
    {:error (str "unknown argument " arg)}))

(defn parse-args
  "Parse launch arguments into {:keys path :log path} or {:error \"...\"}."
  [args]
  (loop [remaining (seq args)
         result {:keys nil :log nil}]
    (if-not remaining
      result
      (let [step (consume result remaining)]
        (if (:error step)
          step
          (recur (seq (:remaining step)) (:result step)))))))

(defn run-steps
  "Thread ctx through steps in order, stopping at the first error. A step takes
   the accumulated map and returns {:error msg} or a map merged into it."
  [ctx steps]
  (reduce (fn [acc step]
            (let [result (step acc)]
              (if (:error result)
                (reduced result)
                (merge acc result))))
          ctx
          steps))

(defn command
  "The command line that starts the game in a child JVM running script-path
   and writing its log to log-path."
  [java-exe classpath script-path log-path]
  [java-exe "-cp" classpath "clojure.main" "-m" "veil.main"
   "--keys" script-path "--log" log-path])

(defn outcome
  "Convert a launch result to an outcome: {:start launched} on success, or
  {:error msg :exit-status 1} on failure. Always includes exit-status 1 for errors."
  [launched]
  (if (:error launched)
    {:error (:error launched) :exit-status 1}
    {:start launched}))
