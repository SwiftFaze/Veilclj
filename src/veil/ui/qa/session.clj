(ns veil.ui.qa.session
  "Running QA procedures end to end, as pure decisions. Everything that touches
  the outside world (files, the game process, the console) is passed in, so
  veil.ui.qa.runner is the only place that spawns or prints.

  io is a map of:
    :exists?          path -> boolean
    :slurp            path -> text
    :spawn            script-path log-path -> {:exit code} or {:timed-out? true}
    :list-procedures  -> file names in specs/qa
  emit! takes {:out [lines] :err [lines]} and prints it."
  (:require [clojure.string :as str]
            [veil.ui.qa.log :as log]
            [veil.ui.qa.procedure :as procedure]))

(def ^:private usage "Usage: bb qa <slug> | bb qa --all")

(defn- failure [message]
  {:out [] :err [message] :status 1})

(defn- load-procedure [io slug]
  (let [path (procedure/path slug)]
    (if ((:exists? io) path)
      (procedure/parse ((:slurp io) path))
      {:error (procedure/unknown-error slug)})))

(defn- play-game
  "Run the game on the procedure's script and read its log back:
   {:entries [...]} or {:error msg}."
  [io slug proc]
  (let [log-path (procedure/log-path slug)
        child ((:spawn io) (:script proc) log-path)]
    (if-let [message (procedure/child-error child)]
      {:error message}
      (log/parse ((:slurp io) log-path)))))

(defn run-one
  "Run one procedure. Returns {:out [lines] :err [lines] :status 0|1}."
  [io slug]
  (let [proc (load-procedure io slug)
        played (if (:error proc) proc (play-game io slug proc))]
    (if (:error played)
      (failure (:error played))
      (procedure/verdict slug (:expect proc)
                         (procedure/check (:entries played) (:expect proc))))))

(defn- finish
  "Emit result and return its status."
  [emit! result]
  (emit! result)
  (:status result))

(defn- run-all
  "Run every procedure in turn, emitting each one's output as it finishes and
   the summary last. Returns the overall status."
  [io emit!]
  (let [listed (procedure/slugs ((:list-procedures io)))]
    (if (:error listed)
      (finish emit! (failure (:error listed)))
      (let [statuses (mapv (fn [slug] [slug (finish emit! (run-one io slug))])
                           (:slugs listed))
            {:keys [line status]} (procedure/summary statuses)]
        (finish emit! {:out [line] :err [] :status status})))))

(defn run
  "Run `bb qa` for args: a slug, or --all. Returns the exit status."
  [io emit! args]
  (let [arg (first args)]
    (cond
      (= arg "--all") (run-all io emit!)
      (and arg (not (str/starts-with? arg "--"))) (finish emit! (run-one io arg))
      :else (finish emit! (failure usage)))))
