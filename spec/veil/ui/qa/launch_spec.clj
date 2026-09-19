(ns veil.ui.qa.launch-spec
  (:require [speclj.core :refer :all]
            [veil.ui.qa.launch :as launch]))

(describe "parse-args"
  (it "returns nil paths when no args given"
    (let [result (launch/parse-args [])]
      (should-be-nil (:keys result))
      (should-be-nil (:log result))))

  (it "parses --keys path"
    (let [result (launch/parse-args ["--keys" "specs/qa/main-menu.keys"])]
      (should= "specs/qa/main-menu.keys" (:keys result))))

  (it "parses --log path"
    (let [result (launch/parse-args ["--log" "target/qa/run.edn"])]
      (should= "target/qa/run.edn" (:log result))))

  (it "parses both --keys and --log"
    (let [result (launch/parse-args ["--keys" "a.keys" "--log" "target/qa/run.edn"])]
      (should= "a.keys" (:keys result))
      (should= "target/qa/run.edn" (:log result))))

  (it "rejects --keys without path"
    (let [result (launch/parse-args ["--keys"])]
      (should= "--keys needs a file path" (:error result))))

  (it "rejects --keys with next flag as path"
    (let [result (launch/parse-args ["--keys" "--log" "path.edn"])]
      (should= "--keys needs a file path" (:error result))))

  (it "rejects --log without path"
    (let [result (launch/parse-args ["--log"])]
      (should= "--log needs a file path" (:error result))))

  (it "rejects --log with next flag as path"
    (let [result (launch/parse-args ["--keys" "a.keys" "--log"])]
      (should= "--log needs a file path" (:error result))))

  (it "rejects unknown flag"
    (let [result (launch/parse-args ["--bogus"])]
      (should= "unknown argument --bogus" (:error result)))))
