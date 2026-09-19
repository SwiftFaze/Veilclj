(ns veil.ui.draw
  "Quil drawing layer: hands each draw command from veil.ui.view to Quil and
  decides nothing (bb shell-check fails the build if it does). It has no specs
  because it needs a live window; the QA run and the playtest cover it."
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
