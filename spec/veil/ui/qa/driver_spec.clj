(ns veil.ui.qa.driver-spec
  (:require [speclj.core :refer :all]
            [veil.ui.qa.driver :as driver]))

(describe "new-driver"
  (it "creates a driver with pending steps and tick 0"
    (let [steps [{:tick 1 :key "Down"} {:tick 2 :key "Enter"}]
          d (driver/new-driver steps)]
      (should= steps (:pending d))
      (should= 0 (:tick d)))))

(describe "advance"
  (it "increments tick"
    (let [d (driver/new-driver [])
          result (driver/advance (constantly {:over? false}) d {:screen :main-menu})]
      (should= 1 (get-in result [:driver :tick]))))

  (it "marks finished when no pending steps"
    (let [d (driver/new-driver [])
          result (driver/advance (constantly {:over? false}) d {:screen :main-menu})]
      (should= true (:finished? result))))

  (it "is not finished when steps remain"
    (let [d (driver/new-driver [{:tick 1 :key "Down"} {:tick 2 :key "Enter"}])
          result (driver/advance (constantly {:over? false}) d {:screen :main-menu})]
      (should= false (:finished? result))))

  (it "presses a step when its tick matches"
    (let [d (driver/new-driver [{:tick 1 :key "Down"}])
          handle (constantly {:over? false})
          result (driver/advance handle d {:screen :main-menu})]
      (should-not (empty? (:entries result))))))
