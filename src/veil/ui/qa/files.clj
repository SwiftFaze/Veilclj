(ns veil.ui.qa.files
  "I/O for QA scripts and logs."
  (:require [veil.ui.qa.script :as script]
            [veil.ui.qa.log :as log])
  (:import [java.nio.file Files Paths StandardOpenOption]))

(defn read-script [path]
  "Read a key script file and parse it.
   Returns {:steps [...]} or {:error \"...\"}."
  (try
    (let [content (slurp path)
          result (script/parse content)]
      (if (:error result)
        {:error (str path ": " (:error result))}
        result))
    (catch Exception e
      {:error (str "cannot read key script " path)})))

(defn start-log! [path]
  "Create parent directories and truncate/create the log file, writing the header."
  (try
    (let [p (Paths/get path (make-array String 0))]
      (Files/createDirectories (.getParent p) (make-array java.nio.file.attribute.FileAttribute 0))
      (spit path (log/render [(log/header)])))
    (catch Exception e
      (throw (ex-info (str "cannot write log " path) {} e)))))

(defn append-log! [path entries]
  "Append entries to the log file."
  (when (seq entries)
    (try
      (spit path (log/render entries) :append true)
      (catch Exception e
        (throw (ex-info (str "cannot write log " path) {} e))))))
