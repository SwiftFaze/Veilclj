(ns veil.mods.ids-spec
  (:require [speclj.core :refer :all]
            [veil.mods.ids :as ids]))

(describe "id-pattern"
  (it "matches valid IDs with alphanumeric namespace and name"
    (should (re-matches ids/id-pattern "core:lever")))

  (it "matches IDs with numbers in namespace and name"
    (should (re-matches ids/id-pattern "core:lever_2")))

  (it "matches IDs with hyphens in namespace and name"
    (should (re-matches ids/id-pattern "core:9-lives")))

  (it "matches IDs with underscores in both parts"
    (should (re-matches ids/id-pattern "mod_name:item_name")))

  (it "rejects IDs without colon"
    (should-not (re-matches ids/id-pattern "lever")))

  (it "rejects IDs with uppercase"
    (should-not (re-matches ids/id-pattern "Core:lever")))

  (it "rejects IDs starting with underscore in namespace"
    (should-not (re-matches ids/id-pattern "_core:lever")))

  (it "rejects IDs starting with underscore in name"
    (should-not (re-matches ids/id-pattern "core:_lever")))

  (it "rejects IDs with empty namespace"
    (should-not (re-matches ids/id-pattern ":lever")))

  (it "rejects IDs with empty name"
    (should-not (re-matches ids/id-pattern "core:")))

  (it "rejects IDs with multiple colons"
    (should-not (re-matches ids/id-pattern "core:a:b"))))

(describe "valid-id?"
  (it "returns true for valid IDs"
    (should (ids/valid-id? "core:lever")))

  (it "returns true for IDs with underscores and hyphens"
    (should (ids/valid-id? "goblin-pack:spear_tip")))

  (it "returns false for invalid IDs"
    (should-not (ids/valid-id? "lever")))

  (it "returns false for IDs with uppercase"
    (should-not (ids/valid-id? "Core:lever")))

  (it "returns false for IDs with multiple colons"
    (should-not (ids/valid-id? "core:a:b"))))

(describe "namespace-of"
  (it "extracts the namespace from a valid ID"
    (should= "core" (ids/namespace-of "core:lever")))

  (it "extracts namespace with hyphens"
    (should= "goblin-pack" (ids/namespace-of "goblin-pack:totem")))

  (it "extracts namespace with underscores"
    (should= "mod_name" (ids/namespace-of "mod_name:item"))))
