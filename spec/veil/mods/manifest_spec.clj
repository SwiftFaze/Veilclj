(ns veil.mods.manifest-spec
  (:require [speclj.core :refer :all]
            [veil.mods.fixtures :as f]
            [veil.mods.manifest :as manifest]))

(defn- problems-for [text]
  (map (juxt :path :expected)
       (:errors (manifest/read-manifests {:folders #{"core"} :files {"core/mod.json" text}}))))

(describe "read-manifests"
  (it "reads each mod's id, dependencies and file"
    (should= {:manifests [{:id "core" :depends-on [] :file "core/mod.json"}
                          {:id "goblin-pack" :depends-on ["core"] :file "goblin-pack/mod.json"}]
              :errors    []}
             (manifest/read-manifests (f/mods (f/manifest "core") (f/manifest "goblin-pack" "core")))))

  (it "defaults dependsOn to none"
    (should= [] (-> (manifest/read-manifests (f/mods ["core/mod.json" "{\"id\": \"core\"}"]))
                    :manifests first :depends-on)))

  (it "reports a folder without a mod.json"
    (should= [{:kind :missing-manifest :folder "stray" :message "mods/stray has no mod.json"}]
             (:errors (manifest/read-manifests {:folders #{"stray"} :files {}}))))

  (it "reports an id that differs from its folder name"
    (let [[error] (:errors (manifest/read-manifests (f/mods ["goblins/mod.json" "{\"id\": \"goblin-pack\"}"])))]
      (should= [:folder-mismatch "goblins/mod.json" "goblin-pack" "goblins"]
               ((juxt :kind :file :id :folder) error))))

  (it "reports a missing id"
    (should= [["/id" "a required field"]] (problems-for "{\"dependsOn\": []}")))

  (it "reports dependsOn that isn't a list"
    (should= [["/dependsOn" "a list of mod ids"]]
             (problems-for "{\"id\": \"core\", \"dependsOn\": \"goblins\"}")))

  (it "reports a dependency that isn't a string"
    (should= [["/dependsOn/0" "a string"]] (problems-for "{\"id\": \"core\", \"dependsOn\": [3]}")))

  (it "rejects fields other than id and dependsOn"
    (should= [["/version" "no such field"]] (problems-for "{\"id\": \"core\", \"version\": \"1.0\"}")))

  (it "collects problems from every manifest"
    (should= [:missing-manifest :invalid]
             (map :kind (:errors (manifest/read-manifests {:folders #{"a" "b"} :files {"b/mod.json" "{}"}}))))))
