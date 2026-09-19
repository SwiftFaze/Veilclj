(ns veil.ui.qa.files
  "I/O for QA scripts and logs."
  (:require [veil.ui.qa.script :as script]
            [veil.ui.qa.log :as log])
  (:import [java.nio.file Files Paths]
           [java.nio.file.attribute FileAttribute]))

(defn- write-error-message [path]
  (str "cannot write log " path))

(defn read-script
  "Read a key script file and parse it.
   Returns {:steps [...]} or {:error \"...\"}."
  [path]
  (try
    (let [result (script/parse (slurp path))]
      (if (:error result)
        {:error (str path ": " (:error result))}
        result))
    (catch Exception _
      {:error (str "cannot read key script " path)})))

(defn start-log!
  "Create parent directories and truncate/create the log file, writing the
   header. Does nothing when no path was asked for. Returns nil, or
   {:error \"...\"} when the log can't be written."
  [path]
  (when path
    (try
      (let [parent (.getParent (Paths/get path (make-array String 0)))]
        (Files/createDirectories parent (make-array FileAttribute 0))
        (spit path (log/render [(log/header)]))
        nil)
      (catch Exception _
        {:error (write-error-message path)}))))

(defn append-log!
  "Append entries to the log file."
  [path entries]
  (when (seq entries)
    (try
      (spit path (log/render entries) :append true)
      (catch Exception e
        (throw (ex-info (write-error-message path) {} e))))))
