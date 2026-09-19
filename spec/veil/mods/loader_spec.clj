(ns veil.mods.loader-spec
  (:require [speclj.core :refer :all]
            [veil.mods.fixtures :as f]
            [veil.mods.loader :as loader]
            [veil.mods.registry :as registry]))

(defn- load-mods [& files]
  (loader/load-mods (apply f/mods files) f/content-types))

(defn- registered [result type id]
  (select-keys (registry/entry (:registry result) type id) [:mod :value]))

(defn- error-kinds [result]
  (map :kind (:errors result)))

(describe "load-mods: registry"
  (it "is empty when there are no mods"
    (should= {:registry {:load-order [] :content {}}}
             (loader/load-mods {:folders #{} :files {}} f/content-types)))

  (it "records the load order"
    (should= ["core" "goblin-pack"]
             (registry/load-order (:registry (load-mods (f/manifest "goblin-pack" "core") (f/manifest "core"))))))

  (it "registers each mod's content under its id with the constructed value"
    (let [result (load-mods (f/manifest "core") (f/manifest "goblin-pack" "core")
                            (f/widget "core" "lever" "core:lever")
                            (f/widget "goblin-pack" "totem" "goblin-pack:totem"))]
      (should= {:mod "core" :value "lever"} (registered result :widget "core:lever"))
      (should= {:mod "goblin-pack" :value "totem"} (registered result :widget "goblin-pack:totem"))))

  (it "loads every content type, each into its own table"
    (let [result (load-mods (f/manifest "core") (f/widget "core" "lever" "core:lever")
                            ["core/gadgets/gear.json" "{\"id\": \"core:gear\"}"])]
      (should= #{"core:lever"} (registry/content-ids (:registry result) :widget))
      (should= #{"core:gear"} (registry/content-ids (:registry result) :gadget))))

  (it "ignores unclaimed folders and non-JSON files"
    (let [result (load-mods (f/manifest "core") (f/widget "core" "lever" "core:lever")
                            ["core/tiles/grass.json" "{ not valid json"]
                            ["core/widgets/README.md" "not json at all"]
                            ["core/widgets/deep/x.json" "{ not valid json"])]
      (should= #{"core:lever"} (registry/content-ids (:registry result) :widget)))))

(describe "load-mods: overrides"
  (it "lets a dependent mod replace an entry"
    (let [result (load-mods (f/manifest "core") (f/manifest "retexture-pack" "core")
                            (f/widget "core" "lever" "core:lever")
                            (f/widget "retexture-pack" "fancy" "core:lever" {:overrides "core:lever"}))]
      (should= {:mod "retexture-pack" :value "fancy"} (registered result :widget "core:lever"))))

  (it "allows the override through a transitive dependency"
    (let [result (load-mods (f/manifest "core") (f/manifest "goblin-pack" "core") (f/manifest "orc-pack" "goblin-pack")
                            (f/widget "core" "lever" "core:lever")
                            (f/widget "orc-pack" "orcish" "core:lever" {:overrides "core:lever"}))]
      (should= {:mod "orc-pack" :value "orcish"} (registered result :widget "core:lever"))))

  (it "keeps the other type's entry with the same id"
    (let [result (load-mods (f/manifest "core") (f/widget "core" "lever" "core:lever")
                            ["core/gadgets/lever.json" "{\"id\": \"core:lever\"}"])]
      (should= {:mod "core" :value "core:lever"} (registered result :gadget "core:lever")))))

(describe "load-mods: errors"
  (it "collects validation problems from manifests and content files together"
    (let [result (load-mods ["goblin-pack/mod.json" "{\"id\": \"goblin-pack\", \"version\": \"1\"}"]
                            (f/manifest "core")
                            ["core/widgets/lever.json" "{\"id\": \"core:lever\", \"label\": 7}"]
                            ["core/widgets/crank.json" "{\"id\": \"core:crank\"}"])]
      (should= #{["goblin-pack/mod.json" "/version"] ["core/widgets/lever.json" "/label"]
                 ["core/widgets/crank.json" "/label"]}
               (set (map (juxt :file :path) (:errors result))))))

  (it "reports an overrides value that differs from the id"
    (let [result (load-mods (f/manifest "core") (f/widget "core" "lever" "core:lever" {:overrides "core:handle"}))]
      (should= [[:overrides-mismatch "core:lever" "core:handle"]]
               (map (juxt :kind :id :overrides) (:errors result)))))

  (it "reports two files in one mod with the same id, naming both"
    (let [result (load-mods (f/manifest "core") (f/widget "core" "lever" "core:lever")
                            (f/widget "core" "lever-old" "core:lever" {:overrides "core:lever"}))]
      (should= [[:duplicate-in-mod ["core/widgets/lever-old.json" "core/widgets/lever.json"]]]
               (map (juxt :kind :files) (:errors result)))))

  (it "reports an ordering problem only once validation passes"
    (should= [:unresolved-dependency] (error-kinds (load-mods (f/manifest "broken-pack" "nonexistent-mod")))))

  (it "reports a duplicate id without overrides, naming both mods"
    (let [result (load-mods (f/manifest "core") (f/manifest "retexture-pack" "core")
                            (f/widget "core" "lever" "core:lever")
                            (f/widget "retexture-pack" "lever" "core:lever"))]
      (should= [[:collision "core:lever" ["core" "retexture-pack"]]]
               (map (juxt :kind :id :mods) (:errors result)))))

  (it "reports overriding an id nobody registered"
    (let [result (load-mods (f/manifest "core") (f/manifest "retexture-pack" "core")
                            (f/widget "retexture-pack" "lever" "core:lever" {:overrides "core:lever"}))]
      (should= [[:nothing-to-override "retexture-pack/widgets/lever.json" "core:lever"]]
               (map (juxt :kind :file :id) (:errors result)))))

  (it "reports overriding a mod it doesn't depend on, even before that mod loads"
    (let [result (load-mods (f/manifest "zzz-pack") (f/manifest "aaa-pack")
                            (f/widget "zzz-pack" "lever" "zzz-pack:lever")
                            (f/widget "aaa-pack" "lever" "zzz-pack:lever" {:overrides "zzz-pack:lever"}))]
      (should= [[:override-without-dependency "aaa-pack" "zzz-pack:lever" "zzz-pack"]]
               (map (juxt :kind :mod :id :target-mod) (:errors result)))))

  (it "reports overriding an existing entry from a mod it doesn't depend on"
    (let [result (load-mods (f/manifest "aaa-pack") (f/manifest "zzz-pack")
                            (f/widget "aaa-pack" "lever" "aaa-pack:lever")
                            (f/widget "zzz-pack" "lever" "aaa-pack:lever" {:overrides "aaa-pack:lever"}))]
      (should= [:override-without-dependency] (error-kinds result))))

  (it "reports a new id in another mod's namespace"
    (let [result (load-mods (f/manifest "core") (f/manifest "goblin-pack" "core")
                            (f/widget "goblin-pack" "spear" "core:spear"))]
      (should= [[:foreign-namespace "goblin-pack/widgets/spear.json" "core:spear" "goblin-pack"]]
               (map (juxt :kind :file :id :mod) (:errors result))))))

(defn- with-check [check]
  (assoc f/widget-type :check check))

(describe "load-mods: content type :check"
  (it "gives the check the data, mod, file and every path in the mods data"
    (let [seen (atom nil)
          mods-data (f/mods (f/manifest "core") (f/widget "core" "lever" "core:lever"))]
      (loader/load-mods mods-data [(with-check (fn [context] (reset! seen context) []))])
      (should= {:data {:id "core:lever" :label "lever"}
                :mod "core"
                :file "core/widgets/lever.json"
                :paths #{"core/mod.json" "core/widgets/lever.json"}}
               @seen)))

  (it "reports what the check returns and registers nothing"
    (let [problem {:kind :invalid :file "core/widgets/lever.json" :path "/label" :message "no good"}
          result (loader/load-mods (f/mods (f/manifest "core") (f/widget "core" "lever" "core:lever"))
                                   [(with-check (constantly [problem]))])]
      (should= [problem] (:errors result))
      (should-not (contains? result :registry))))

  (it "registers the entry when the check finds nothing"
    (let [result (loader/load-mods (f/mods (f/manifest "core") (f/widget "core" "lever" "core:lever"))
                                   [(with-check (constantly []))])]
      (should= {:mod "core" :value "lever"}
               (select-keys (registry/entry (:registry result) :widget "core:lever") [:mod :value]))))

  (it "does not call the check on a file that fails its spec"
    (let [called (atom false)
          result (loader/load-mods (f/mods (f/manifest "core")
                                           ["core/widgets/lever.json" "{\"id\": \"core:lever\"}"])
                                   [(with-check (fn [_] (reset! called true) []))])]
      (should= ["/label"] (map :path (:errors result)))
      (should-not @called))))

(describe "mods-dir"
  (it "is the property's path when the property is set"
    (should= "/opt/veil/app/mods" (loader/mods-dir "/opt/veil/app/mods")))

  (it "falls back to 'mods' when the property is not set"
    (should= "mods" (loader/mods-dir nil)))

  (it "falls back to 'mods' when the property is blank"
    (should= "mods" (loader/mods-dir "  "))))

(describe "error-report"
  (it "lists every problem's message under a heading"
    (should= "Failed to load mods:\n  a.json: broken\n  b.json: broken"
             (loader/error-report [{:message "a.json: broken"} {:message "b.json: broken"}]))))

(describe "startup"
  (it "returns registry on clean load"
    (let [result (loader/startup (f/mods (f/manifest "core")) f/content-types)]
      (should (contains? result :registry))
      (should-not (contains? result :error))))

  (it "returns no error on clean load"
    (let [result (loader/startup (f/mods (f/manifest "core")) f/content-types)]
      (should-be-nil (:error result))))

  (it "returns error and exit-status 1 on failed load"
    (let [result (loader/startup (f/mods (f/manifest "broken-pack" "nonexistent-mod")) f/content-types)]
      (should (contains? result :error))
      (should= 1 (:exit-status result))))

  (it "error report starts with 'Failed to load mods:'"
    (let [result (loader/startup (f/mods (f/manifest "broken-pack" "nonexistent-mod")) f/content-types)]
      (should (.startsWith (:error result) "Failed to load mods:"))))

  (it "error report names all problems"
    (let [result (loader/startup
                   (f/mods (f/manifest "core")
                           ["core/widgets/lever.json" "{\"id\": \"core:lever\"}"]
                           ["core/widgets/crank.json" "{\"id\": \"core:crank\"}"])
                   f/content-types)]
      (should (.contains (:error result) "core/widgets/lever.json"))
      (should (.contains (:error result) "core/widgets/crank.json")))))
