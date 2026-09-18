(ns veil.mods.disk
  "The mods layer's only I/O: reads mods/ into plain data for veil.mods.loader.
  It reads text without interpreting it; deciding which files count is the
  loader's job, so this stays too simple to need mutation testing."
  (:require [clojure.java.io :as io]))

(defn- subdirs [^java.io.File dir]
  (sort-by #(.getName ^java.io.File %) (filter #(.isDirectory ^java.io.File %) (.listFiles dir))))

(defn- plain-files [^java.io.File dir]
  (filter #(.isFile ^java.io.File %) (.listFiles dir)))

(defn- mod-files
  "path -> text for a mod folder's mod.json and every file one folder below it."
  [^java.io.File mod-dir]
  (let [folder (.getName mod-dir)
        manifest (io/file mod-dir "mod.json")]
    (into (if (.isFile manifest) {(str folder "/mod.json") (slurp manifest)} {})
          (for [sub (subdirs mod-dir)
                ^java.io.File f (plain-files sub)]
            [(str folder "/" (.getName ^java.io.File sub) "/" (.getName f)) (slurp f)]))))

(defn read-mods-dir
  "{:folders #{names} :files {\"core/mod.json\" text, \"core/widgets/a.json\" text}}
  for the mods directory at root; empty when it doesn't exist."
  [root]
  (let [dirs (subdirs (io/file root))]
    {:folders (set (map #(.getName ^java.io.File %) dirs))
     :files   (into {} (mapcat mod-files dirs))}))
