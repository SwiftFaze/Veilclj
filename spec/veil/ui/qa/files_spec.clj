(ns veil.ui.qa.files-spec
  (:require [clojure.java.io :as io]
            [speclj.core :refer :all]
            [veil.ui.qa.files :as files])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- temp-dir []
  (.toFile (Files/createTempDirectory "veil-qa" (make-array FileAttribute 0))))

(defn- delete-tree! [f]
  (doseq [x (reverse (file-seq f))] (io/delete-file x true)))

(defn- path-in [root & names]
  (str (apply io/file root names)))

(describe "read-script"
  (with-all root (temp-dir))
  (after-all (delete-tree! @root))

  (it "reads and parses a script file"
    (let [path (path-in @root "ok.keys")]
      (spit path "Down\nEnter\n")
      (should= {:steps [{:tick 1 :key "Down"} {:tick 2 :key "Enter"}]}
               (files/read-script path))))

  (it "prefixes a parse error with the file path"
    (let [path (path-in @root "bad.keys")]
      (spit path "Bogus\n")
      (should= (str path ": line 1: unknown key \"Bogus\"")
               (:error (files/read-script path)))))

  (it "reports a script that cannot be read"
    (let [path (path-in @root "absent.keys")]
      (should= (str "cannot read key script " path)
               (:error (files/read-script path))))))

(describe "start-log!"
  (with-all root (temp-dir))
  (after-all (delete-tree! @root))

  (it "creates missing parent directories and writes the version header"
    (let [path (path-in @root "deep" "er" "run.log.edn")]
      (should-be-nil (files/start-log! path))
      (should= "{:log/version 1}\n" (slurp path))))

  (it "truncates a log left from an earlier run"
    (let [path (path-in @root "again.log.edn")]
      (spit path "{:log/version 1}\n{:tick 1 :key :down}\n")
      (files/start-log! path)
      (should= "{:log/version 1}\n" (slurp path))))

  (it "does nothing when no log was asked for"
    (should-be-nil (files/start-log! nil)))

  (it "reports a log that cannot be written"
    (let [blocker (path-in @root "blocker")
          path (path-in @root "blocker" "run.log.edn")]
      (spit blocker "a file, not a directory")
      (should= {:error (str "cannot write log " path)} (files/start-log! path))))

  (it "writes to a bare relative filename (no directory) in the working directory"
    (let [bare-name (str "veil-qa-test-" (System/nanoTime) ".log.edn")]
      (try
        (should-be-nil (files/start-log! bare-name))
        (should= "{:log/version 1}\n" (slurp bare-name))
        (finally
          (io/delete-file bare-name true))))))

(describe "append-log!"
  (with-all root (temp-dir))
  (after-all (delete-tree! @root))

  (it "appends entries after what is already there, one per line"
    (let [path (path-in @root "append.log.edn")]
      (files/start-log! path)
      (files/append-log! path [{:tick 1 :key :down}])
      (files/append-log! path [{:tick 2 :key :enter}])
      (should= "{:log/version 1}\n{:tick 1, :key :down}\n{:tick 2, :key :enter}\n"
               (slurp path))))

  (it "writes nothing for no entries"
    (let [path (path-in @root "quiet.log.edn")]
      (files/start-log! path)
      (files/append-log! path [])
      (should= "{:log/version 1}\n" (slurp path))))

  (it "does not need a log path when there is nothing to write"
    (should-be-nil (files/append-log! nil [])))

  (it "writes nothing, and does not throw, when entries arrive but no log was asked for"
    (should-be-nil (files/append-log! nil [{:tick 1 :key :down}])))

  (it "reports a log that cannot be written"
    (let [path (path-in @root "no-such-dir" "x.log.edn")]
      (should-throw clojure.lang.ExceptionInfo
                    (str "cannot write log " path)
                    (files/append-log! path [{:tick 1 :key :down}])))))
