(ns veil.ui.qa.procedure-spec
  (:require [speclj.core :refer :all]
            [veil.ui.qa.procedure :as procedure]))

(describe "parse"
  (it "parses a procedure with script and expect"
    (let [edn-text "{:script \"specs/qa/x.keys\" :expect [{:key :down}]}"
          result (procedure/parse edn-text)]
      (should-be-nil (:error result))
      (should= "specs/qa/x.keys" (:script result))
      (should= [{:key :down}] (:expect result))))

  (it "rejects missing :script"
    (let [edn-text "{:expect [{:key :down}]}"
          result (procedure/parse edn-text)]
      (should= "missing :script" (:error result))))

  (it "rejects missing :expect"
    (let [edn-text "{:script \"specs/qa/x.keys\"}"
          result (procedure/parse edn-text)]
      (should= "missing :expect" (:error result))))

  (it "rejects empty :expect"
    (let [edn-text "{:script \"specs/qa/x.keys\" :expect []}"
          result (procedure/parse edn-text)]
      (should= "expects nothing" (:error result)))))

(describe "check"
  (it "marks entries as seen when they match in order"
    (let [log-entries [{:tick 1 :key :down} {:tick 1 :event :menu/selection-changed :to :options}]
          expected [{:key :down} {:event :menu/selection-changed}]
          result (procedure/check log-entries expected)]
      (should= [:seen :seen] (:report result))
      (should= 0 (:status result))))

  (it "marks entries as missing when not found"
    (let [log-entries [{:tick 1 :key :down}]
          expected [{:key :down} {:event :menu/selection-changed}]
          result (procedure/check log-entries expected)]
      (should= [:seen :missing] (:report result))
      (should= 1 (:status result))))

  (it "allows partial matching of entries"
    (let [log-entries [{:tick 1 :key :down :extra :field}]
          expected [{:key :down}]
          result (procedure/check log-entries expected)]
      (should= [:seen] (:report result))
      (should= 0 (:status result))))

  (it "enforces order: must find after previous match"
    (let [log-entries [{:tick 1 :key :enter} {:tick 2 :key :down}]
          expected [{:key :down} {:key :enter}]
          result (procedure/check log-entries expected)]
      (should= [:seen :missing] (:report result))
      (should= 1 (:status result))))

  (it "one log entry cannot satisfy two expectations"
    (let [log-entries [{:tick 1 :key :down}]
          expected [{:key :down} {:key :down}]
          result (procedure/check log-entries expected)]
      (should= [:seen :missing] (:report result))
      (should= 1 (:status result))))

  (it "returns all seen for full match"
    (let [log-entries [{:tick 1 :key :down} {:tick 1 :event :menu/selection-changed :to :options}]
          expected [{:event :menu/selection-changed :to :options}]
          result (procedure/check log-entries expected)]
      (should= [:seen] (:report result))
      (should= 0 (:status result)))))

(describe "path"
  (it "returns specs/qa/<slug>.edn"
    (should= "specs/qa/main-menu.edn" (procedure/path "main-menu"))))

(describe "unknown-error"
  (it "returns unknown procedure message"
    (should= "no QA procedure for foo (expected specs/qa/foo.edn)" (procedure/unknown-error "foo"))))

(describe "slugs"
  (it "extracts slugs from .edn filenames, sorted"
    (let [file-names ["main-menu.edn" "alpha.edn" "zeta.edn"]
          result (procedure/slugs file-names)]
      (should= {:slugs ["alpha" "main-menu" "zeta"]} result)))

  (it "ignores non-.edn files"
    (let [file-names ["main-menu.edn" "notes.md" "main-menu.keys"]
          result (procedure/slugs file-names)]
      (should= {:slugs ["main-menu"]} result)))

  (it "returns error when no .edn files"
    (let [file-names []
          result (procedure/slugs file-names)]
      (should= {:error "no QA procedures in specs/qa"} result)))

  (it "returns error when all files are non-.edn"
    (let [file-names ["notes.md" "main-menu.keys"]
          result (procedure/slugs file-names)]
      (should= {:error "no QA procedures in specs/qa"} result))))

(describe "summary"
  (it "counts passes and fails"
    (let [results [["main-menu" 0]]
          result (procedure/summary results)]
      (should= "QA all: 1 passed, 0 failed" (:line result))
      (should= 0 (:status result))))

  (it "counts multiple passes"
    (let [results [["a" 0] ["b" 0]]
          result (procedure/summary results)]
      (should= "QA all: 2 passed, 0 failed" (:line result))
      (should= 0 (:status result))))

  (it "counts failures and lists them"
    (let [results [["a" 0] ["b" 1]]
          result (procedure/summary results)]
      (should= "QA all: 1 passed, 1 failed (b)" (:line result))
      (should= 1 (:status result))))

  (it "lists all failed slugs in order"
    (let [results [["a" 1] ["b" 1]]
          result (procedure/summary results)]
      (should= "QA all: 0 passed, 2 failed (a, b)" (:line result))
      (should= 1 (:status result))))

  (it "status is 1 if any failure exists"
    (let [results [["a" 1] ["b" 0]]
          result (procedure/summary results)]
      (should= 1 (:status result)))))
