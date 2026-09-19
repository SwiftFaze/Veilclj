(ns veil.ui.qa.log-spec
  (:require [speclj.core :refer :all]
            [veil.ui.qa.log :as log]))

(describe "header"
  (it "returns a map with version 1"
    (should= {:log/version 1} (log/header))))

(describe "render"
  (it "renders header as plain map, not namespaced"
    (let [header (log/header)
          text (log/render [header])]
      (should= "{:log/version 1}\n" text)))

  (it "renders entries as pr-str one per line"
    (let [entries [{:tick 1 :key :down} {:tick 1 :event :menu/selection-changed :to :options}]
          text (log/render entries)]
      (should (clojure.string/includes? text "{:tick 1, :key :down}"))
      (should (clojure.string/includes? text "{:tick 1, :event :menu/selection-changed, :to :options}"))
      (should (clojure.string/ends-with? text "\n"))))

  (it "ends with newline"
    (let [entries [{:tick 1 :key :down}]
          text (log/render entries)]
      (should (clojure.string/ends-with? text "\n")))))

(describe "parse"
  (it "parses log text with version line"
    (let [text "{:log/version 1}\n{:tick 1 :key :down}\n"
          result (log/parse text)]
      (should-be-nil (:error result))
      (should= [{:tick 1 :key :down}] (:entries result))))

  (it "rejects log with no version line"
    (let [text "{:tick 1 :key :down}\n"
          result (log/parse text)]
      (should= "log has no version line" (:error result))))

  (it "rejects empty log"
    (let [text ""
          result (log/parse text)]
      (should= "log has no version line" (:error result))))

  (it "rejects unsupported version"
    (let [text "{:log/version 2}\n"
          result (log/parse text)]
      (should= "unsupported log version 2 (this runner reads 1)" (:error result))))

  (it "strips header on parse"
    (let [text "{:log/version 1}\n{:tick 1 :key :down}\n{:tick 2 :key :enter}\n"
          result (log/parse text)]
      (should= 2 (count (:entries result))))))

(describe "parse rejections"
  (it "rejects a first line that is not EDN"
    (should= "log has no version line" (:error (log/parse "not edn ((\n"))))

  (it "rejects a first line that is not a map"
    (should= "log has no version line" (:error (log/parse "[1 2]\n"))))

  (it "rejects a header map without a version"
    (should= "log has no version line" (:error (log/parse "{:other 1}\n"))))

  (it "ignores blank lines and reads CRLF line endings"
    (let [result (log/parse "\r\n{:log/version 1}\r\n\r\n{:tick 1 :key :down}\r\n")]
      (should= [{:tick 1 :key :down}] (:entries result))))

  (it "round-trips what render writes"
    (let [entries [{:tick 1 :key :down} {:tick 1 :event :screen/changed :from :main-menu :to :map}]
          text (log/render (cons (log/header) entries))]
      (should= entries (:entries (log/parse text))))))
