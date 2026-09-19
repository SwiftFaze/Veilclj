(ns veil.ui.qa.runner
  "Entry point for `bb qa`: the real process, file and console I/O behind
  veil.ui.qa.session. Nothing here decides anything; it is not specced, since
  the one thing it does that matters is opening the game window."
  (:require [clojure.java.io :as io]
            [veil.ui.qa.launch :as launch]
            [veil.ui.qa.session :as session])
  (:import [java.util.concurrent TimeUnit])
  (:gen-class))

(def ^:private timeout-seconds 60)

(defn- spawn!
  "Run the game in a child JVM on script-path, logging to log-path. Returns
   {:exit code}, or {:timed-out? true} after killing it."
  [script-path log-path]
  (let [command (launch/command (str (System/getProperty "java.home") "/bin/java")
                                (System/getProperty "java.class.path")
                                script-path
                                log-path)
        child (.start (.inheritIO (ProcessBuilder. ^java.util.List command)))]
    (if (.waitFor child timeout-seconds TimeUnit/SECONDS)
      {:exit (.exitValue child)}
      (do (.destroyForcibly child)
          {:timed-out? true}))))

(defn- list-procedures
  "File names in specs/qa; empty when the directory is missing (.list gives nil)."
  []
  (vec (.list (io/file "specs/qa"))))

(defn- emit! [{:keys [out err]}]
  (run! println out)
  (binding [*out* *err*]
    (run! println err)))

(def ^:private real-io
  {:exists?         #(.exists (io/file %))
   :slurp           slurp
   :spawn           spawn!
   :list-procedures list-procedures})

(defn -main [& args]
  (System/exit (session/run real-io emit! args)))
