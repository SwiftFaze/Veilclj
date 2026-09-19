(ns veil.property-support-spec
  (:require [speclj.core :refer :all]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [veil.property-support :as support]))

;; A deliberately failing property: asserts that all vectors have fewer than 3 elements.
;; This will fail on vectors of 3+ elements, shrinking to [0 0 0].
(def failing-property
  (prop/for-all [xs (gen/vector gen/small-integer)]
    (< (count xs) 3)))

;; A property that always holds.
(def passing-property
  (prop/for-all [x gen/small-integer]
    true))

(describe "support/run with passing property"
  (it "returns nil when the property holds"
    (should-be-nil (support/run passing-property))))

(describe "support/run with failing property"
  (it "returns a message string on failure"
    (let [message (support/run failing-property 12345)]
      (should-not-be-nil message)
      (should (string? message))))

  (it "includes the seed in the message"
    (let [message (support/run failing-property 12345)]
      (should (clojure.string/includes? message "Seed: 12345"))))

  (it "includes a shrunk counterexample in the message"
    (let [message (support/run failing-property 12345)]
      (should (clojure.string/includes? message "Shrunk counterexample:"))))

  (it "includes a rerun hint with VEIL_PROPERTY_SEED in the message"
    (let [message (support/run failing-property 12345)]
      (should (clojure.string/includes? message "VEIL_PROPERTY_SEED="))))

  (it "produces identical results when run twice with the same seed"
    (let [msg1 (support/run failing-property 12345)
          msg2 (support/run failing-property 12345)]
      (should= msg1 msg2))))
