(ns veil.mods.registry-spec
  (:require [speclj.core :refer :all]
            [veil.mods.registry :as registry]))

(describe "empty-registry"
  (it "returns registry with empty load order"
    (let [reg (registry/empty-registry)]
      (should= [] (registry/load-order reg))))

  (it "returns registry with empty content"
    (let [reg (registry/empty-registry)]
      (should= {} (:content reg)))))

(describe "load-order"
  (it "returns empty vector for empty registry"
    (let [reg (registry/empty-registry)]
      (should= [] (registry/load-order reg))))

  (it "returns load order from registry"
    (let [reg (registry/with-load-order (registry/empty-registry) ["core" "goblin-pack"])]
      (should= ["core" "goblin-pack"] (registry/load-order reg)))))

(describe "entry"
  (it "returns nil for non-existent entry"
    (let [reg (registry/empty-registry)]
      (should-be-nil (registry/entry reg :widget "core:lever"))))

  (it "returns entry data"
    (let [reg (-> (registry/empty-registry)
                  (registry/register-entry :widget "core:lever" "core" "core/widgets/lever.json" {:label "Lever"}))]
      (let [entry (registry/entry reg :widget "core:lever")]
        (should= "core" (:mod entry))
        (should= "core/widgets/lever.json" (:file entry))
        (should= {:label "Lever"} (:value entry)))))

  (it "returns nil for non-existent type"
    (let [reg (-> (registry/empty-registry)
                  (registry/register-entry :widget "core:lever" "core" "core/widgets/lever.json" {:label "Lever"}))]
      (should-be-nil (registry/entry reg :gadget "core:lever")))))

(describe "content-ids"
  (it "returns empty set for empty registry"
    (let [reg (registry/empty-registry)]
      (should= #{} (registry/content-ids reg :widget))))

  (it "returns empty set for non-existent type"
    (let [reg (-> (registry/empty-registry)
                  (registry/register-entry :widget "core:lever" "core" "core/widgets/lever.json" {}))]
      (should= #{} (registry/content-ids reg :gadget))))

  (it "returns all IDs for a type"
    (let [reg (-> (registry/empty-registry)
                  (registry/register-entry :widget "core:lever" "core" "core/widgets/lever.json" {})
                  (registry/register-entry :widget "core:crank" "core" "core/widgets/crank.json" {}))]
      (should= #{"core:lever" "core:crank"} (registry/content-ids reg :widget))))

  (it "only returns IDs for requested type"
    (let [reg (-> (registry/empty-registry)
                  (registry/register-entry :widget "core:lever" "core" "core/widgets/lever.json" {})
                  (registry/register-entry :gadget "core:gear" "core" "core/gadgets/gear.json" {}))]
      (should= #{"core:lever"} (registry/content-ids reg :widget))
      (should= #{"core:gear"} (registry/content-ids reg :gadget)))))

(describe "register-entry"
  (it "creates new entry in registry"
    (let [reg (registry/register-entry (registry/empty-registry) :widget "core:lever" "core" "core/widgets/lever.json" {:label "Lever"})]
      (should= {:mod "core" :file "core/widgets/lever.json" :value {:label "Lever"}}
               (registry/entry reg :widget "core:lever"))))

  (it "can register multiple entries"
    (let [reg (-> (registry/empty-registry)
                  (registry/register-entry :widget "core:lever" "core" "core/widgets/lever.json" {})
                  (registry/register-entry :widget "core:crank" "core" "core/widgets/crank.json" {}))]
      (should= 2 (count (registry/content-ids reg :widget)))))

  (it "can register multiple types"
    (let [reg (-> (registry/empty-registry)
                  (registry/register-entry :widget "core:lever" "core" "core/widgets/lever.json" {})
                  (registry/register-entry :gadget "core:gear" "core" "core/gadgets/gear.json" {}))]
      (should= 1 (count (registry/content-ids reg :widget)))
      (should= 1 (count (registry/content-ids reg :gadget))))))

(describe "with-load-order"
  (it "sets load order in registry"
    (let [reg (registry/with-load-order (registry/empty-registry) ["core" "goblin-pack"])]
      (should= ["core" "goblin-pack"] (registry/load-order reg))))

  (it "overwrites existing load order"
    (let [reg (-> (registry/empty-registry)
                  (registry/with-load-order ["core"])
                  (registry/with-load-order ["core" "goblin-pack"]))]
      (should= ["core" "goblin-pack"] (registry/load-order reg)))))
