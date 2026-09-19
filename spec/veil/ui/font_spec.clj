(ns veil.ui.font-spec
  (:require [speclj.core :refer :all]
    [veil.ui.font :as font])
  (:import [java.io File]))

(describe "open"
  (it "returns font-path and size when the file can be opened"
    (let [mods-dir "mods"
          f {:file "core/fonts/JetBrainsMono-Regular.ttf" :size 20}
          result (font/open mods-dir f)]
      (should (contains? result :font-path))
      (should (contains? result :size))
      (should= 20 (:size result))
      (should (.endsWith (:font-path result) "JetBrainsMono-Regular.ttf"))))

  (it "returns error for a file with junk bytes"
    (let [temp-dir (System/getProperty "java.io.tmpdir")
          bad-file (str temp-dir "/Bad.ttf")
          _ (spit bad-file "not a font")
          f {:file bad-file :size 20}
          result (font/open temp-dir f)]
      (try
        (should (contains? result :error))
        (should (.contains (:error result) "Bad.ttf"))
        (finally (java.nio.file.Files/delete (java.nio.file.Paths/get bad-file (make-array String 0)))))))

  (it "returns error for a missing file"
    (let [mods-dir "."
          f {:file "nonexistent/Nope.ttf" :size 20}
          result (font/open mods-dir f)]
      (should (contains? result :error))
      (should (.contains (:error result) "not a usable font"))))

  (it "returns size unchanged"
    (let [mods-dir "mods"
          f {:file "core/fonts/JetBrainsMono-Regular.ttf" :size 24}
          result (font/open mods-dir f)]
      (should= 24 (:size result)))))
