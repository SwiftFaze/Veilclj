(ns veil.ui.draw
  "Quil drawing layer. Kept thin: only this ns touches Quil.
  No specs possible here. It must stay trivial."
  (:require [quil.core :as q]
            [veil.ui.view :as view]))

(defn draw!
  "Draw the current game state using Quil. Called once per frame."
  [state]
  (q/background 0)
  (q/text-align :center :center)
  (doseq [{:keys [text x y color]} (view/frame state (q/width))]
    (q/fill color)
    (q/text text x y))
  state)
