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

(describe "parse-args flags"
  (it "rejects a stray word that is not a flag"
    (should= "unknown argument stray" (:error (launch/parse-args ["stray"]))))

  (it "rejects an unknown flag after valid ones"
    (should= "unknown argument --bogus"
             (:error (launch/parse-args ["--keys" "a.keys" "--bogus"]))))

  (it "keeps the last value when a flag repeats"
    (should= "b.keys" (:keys (launch/parse-args ["--keys" "a.keys" "--keys" "b.keys"]))))

  (it "accepts a path that merely contains dashes"
    (should= "my-script.keys" (:keys (launch/parse-args ["--keys" "my-script.keys"])))))

(describe "run-steps"
  (it "merges each step's result into the context"
    (should= {:a 1 :b 2 :c 3}
             (launch/run-steps {:a 1} [(fn [_] {:b 2}) (fn [ctx] {:c (+ (:a ctx) (:b ctx))})])))

  (it "passes the context through untouched when a step returns nil"
    (should= {:a 1} (launch/run-steps {:a 1} [(fn [_] nil)])))

  (it "stops at the first error and runs no later step"
    (let [ran (atom [])
          step (fn [label result] (fn [_] (swap! ran conj label) result))]
      (should= {:error "boom"}
               (launch/run-steps {} [(step :one {:x 1}) (step :two {:error "boom"}) (step :three {:y 2})]))
      (should= [:one :two] @ran)))

  (it "returns the context when there are no steps"
    (should= {:a 1} (launch/run-steps {:a 1} []))))

(describe "command"
  (it "builds the child JVM command line for a script and its log"
    (should= ["java" "-cp" "cp" "clojure.main" "-m" "veil.main"
              "--keys" "s.keys" "--log" "l.edn"]
             (launch/command "java" "cp" "s.keys" "l.edn"))))

(describe "outcome"
  (it "wraps a successful launch with :start key"
    (let [launched {:registry "r" :qa "q"}]
      (should= {:start launched} (launch/outcome launched))))

  (it "extracts error and exit-status from a failed launch"
    (let [launched {:error "Boom!" :exit-status 1}]
      (should= {:error "Boom!" :exit-status 1} (launch/outcome launched))))

  (it "preserves other keys in a successful launch"
    (let [launched {:registry "r" :qa "q" :extra "value"}]
      (should= {:start launched} (launch/outcome launched))))

  (it "adds exit-status 1 to a bad-argument error that has no exit-status"
    (let [launched {:error "unknown argument --bogus"}]
      (should= {:error "unknown argument --bogus" :exit-status 1} (launch/outcome launched)))))
