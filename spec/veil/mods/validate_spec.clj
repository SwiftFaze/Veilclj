(ns veil.mods.validate-spec
  (:require [speclj.core :refer :all]
            [veil.mods.fixtures :as f]
            [veil.mods.validate :as validate]))

(defn- errors-for [text]
  (:errors (validate/validate "core/widgets/w.json" text (:spec f/widget-type))))

(defn- paths-and-expectations [text]
  (set (map (juxt :path :expected) (errors-for text))))

(describe "validate"
  (it "returns the parsed data for a valid file"
    (should= {:data {:id "core:lever" :label "L"}}
             (validate/validate "f.json" "{\"id\": \"core:lever\", \"label\": \"L\"}" (:spec f/widget-type))))

  (it "names the file when the text isn't JSON"
    (let [[error] (errors-for "{ not valid json")]
      (should= [:invalid-json "core/widgets/w.json"] ((juxt :kind :file) error))))

  (it "reports a missing required field at its path"
    (should= #{["/label" "a required field"]}
             (paths-and-expectations "{\"id\": \"core:lever\"}")))

  (it "reports a wrong scalar type"
    (should= #{["/label" "a string"]}
             (paths-and-expectations "{\"id\": \"core:lever\", \"label\": 7}")))

  (it "reports a value inside a map-of without spec's key/value index"
    (should= #{["/colors/BORDER/r" "an integer"]}
             (paths-and-expectations
               "{\"id\": \"core:lever\", \"label\": \"L\", \"colors\": {\"BORDER\": {\"r\": \"x\", \"g\": 0, \"b\": 0}}}")))

  (it "reports a missing key inside a nested map"
    (should= #{["/colors/BORDER/b" "a required field"]}
             (paths-and-expectations
               "{\"id\": \"core:lever\", \"label\": \"L\", \"colors\": {\"BORDER\": {\"r\": 1, \"g\": 0}}}")))

  (it "rejects an unknown top-level field"
    (should= #{["/lable" "no such field"]}
             (paths-and-expectations "{\"id\": \"core:lever\", \"label\": \"L\", \"lable\": \"x\"}")))

  (it "rejects an unknown field nested in a map-of value"
    (should= #{["/colors/BORDER/a" "no such field"]}
             (paths-and-expectations
               "{\"id\": \"core:lever\", \"label\": \"L\", \"colors\": {\"BORDER\": {\"r\": 1, \"g\": 0, \"b\": 0, \"a\": 1}}}")))

  (it "reports an object where one was expected"
    (should= #{["/colors" "an object"]}
             (paths-and-expectations "{\"id\": \"core:lever\", \"label\": \"L\", \"colors\": 3}")))

  (it "reports every problem in the file"
    (should= #{["/label" "a string"] ["/extra" "no such field"]}
             (paths-and-expectations "{\"id\": \"core:lever\", \"label\": 1, \"extra\": 2}")))

  (it "reports a malformed id with its value"
    (let [[error] (errors-for "{\"id\": \"Core:lever\", \"label\": \"L\"}")]
      (should= [:malformed-id "/id" "Core:lever"] ((juxt :kind :path :value) error))))

  (it "puts the file, path and expectation in the message"
    (should= "core/widgets/w.json: /label expected a string"
             (:message (first (errors-for "{\"id\": \"core:lever\", \"label\": 7}")))))

  (it "words a collection mismatch with the caller's phrase for that spec"
    (let [{:keys [errors]} (validate/validate "w.json" "{\"id\": \"a:b\", \"label\": \"L\", \"colors\": []}"
                                              (:spec f/widget-type) {:widget/colors "a colour table"})]
      (should= [["/colors" "a colour table"]] (map (juxt :path :expected) errors)))))
