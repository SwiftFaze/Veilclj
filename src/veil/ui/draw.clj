(ns veil.ui.draw
  "Quil drawing layer. Kept thin and near-branchless: only this ns touches Quil.
  No specs possible here. It must stay trivial."
  (:require [quil.core :as q]
            [veil.ui.view :as view]))

(defn draw!
  "Draw the current game state using Quil. Called once per frame."
  [state]
  (q/background 0)
  (q/fill 220)
  (q/text-align :center :center)
  (doseq [{:keys [text row selected?]} (view/frame state)]
    (q/fill (if selected? [255 255 100] [220 220 220]))
    (q/text text (/ (q/width) 2) (+ 50 (* row 40))))
  state)
