(ns veil.property-support-spec
  (:require [speclj.core :refer :all]
            [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [veil.property-support :as support]))

;; A deliberately failing property: asserts that all vectors have fewer than 3 elements.
;; This fails on vectors of 3+ elements; test.check shrinks the counterexample to a short vector.
(def failing-property
  (prop/for-all [xs (gen/vector gen/small-integer)]
    (< (count xs) 3)))

;; A property that always holds.
(def passing-property
  (prop/for-all [_x gen/small-integer]
    true))

(describe "support/run with passing property"
  (it "returns nil when the property holds"
    (should-be-nil (support/run passing-property))))

(describe "support/run with failing property"
  (with-all message (support/run failing-property 12345))

  (it "returns a message string on failure"
    (should (string? @message)))

  (it "includes the seed in the message"
    (should (str/includes? @message "Seed: 12345")))

  (it "includes a shrunk counterexample in the message"
    (should (str/includes? @message "Shrunk counterexample:")))

  (it "includes a rerun hint with VEIL_PROPERTY_SEED in the message"
    (should (str/includes? @message "VEIL_PROPERTY_SEED=")))

  (it "produces identical results when run twice with the same seed"
    (should= @message (support/run failing-property 12345))))
