(ns veil.mods.disk-spec
  (:require [clojure.java.io :as io]
            [speclj.core :refer :all]
            [veil.mods.disk :as disk])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- temp-dir []
  (.toFile (Files/createTempDirectory "veil-mods" (make-array FileAttribute 0))))

(defn- write! [root path text]
  (let [f (io/file root path)]
    (io/make-parents f)
    (spit f text)))

(defn- delete-tree! [f]
  (doseq [x (reverse (file-seq f))] (io/delete-file x true)))

(describe "read-mods-dir"
  (with-all root (temp-dir))
  (after-all (delete-tree! @root))

  (it "is empty when the directory doesn't exist"
    (should= {:folders #{} :files {}} (disk/read-mods-dir (io/file @root "absent"))))

  (it "reads every mod folder's manifest and the files one folder below it"
    (write! @root "mods/core/mod.json" "{\"id\": \"core\"}")
    (write! @root "mods/core/widgets/lever.json" "{}")
    (write! @root "mods/core/widgets/README.md" "notes")
    (write! @root "mods/stray/tiles/grass.json" "[]")
    (write! @root "mods/core/widgets/deep/ignored.json" "{}")
    (write! @root "mods/loose.json" "{}")
    (should= {:folders #{"core" "stray"}
              :files   {"core/mod.json"           "{\"id\": \"core\"}"
                        "core/widgets/lever.json" "{}"
                        "core/widgets/README.md"  "notes"
                        "stray/tiles/grass.json"  "[]"}}
             (disk/read-mods-dir (io/file @root "mods")))))
