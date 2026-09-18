(ns veil.acceptance.steps.window
  (:require [veil.acceptance.step-support :refer [ok check]]
            [veil.main :as main]))

(def handlers
  [[#"the game is launched"
    (fn [world _]
      (swap! world assoc :window main/window)
      (ok))]
   [#"the window title is (.+)"
    (fn [world [_ title]]
      (check (= title (get-in @world [:window :title]))
             (str "window title is " (get-in @world [:window :title]))))]
   [#"the window is (\d+) by (\d+) pixels"
    (fn [world [_ width height]]
      (let [expected [(parse-long width) (parse-long height)]
            actual   (get-in @world [:window :size])]
        (check (= expected actual)
               (str "window size is " actual))))]])
