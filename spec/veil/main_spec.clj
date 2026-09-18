(ns veil.main-spec
  (:require [speclj.core :refer :all]
            [veil.main :as main]))

(describe "the game window"
  (it "is titled Veil"
    (should= "Veil" (:title main/window)))

  (it "has a positive width and height"
    (should (every? pos? (:size main/window)))))
