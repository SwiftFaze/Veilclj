(ns veil.ui.draw
  "Quil drawing layer: has no decisions (bb shell-check passes).
   It receives draw commands and carries them out. No specs - needs a live window."
  (:require [quil.core :as q]
            [veil.ui.view :as view]))

(defn draw!
  "Draw the current game state using Quil. Called once per frame."
  [state]
  (let [{:keys [background rects glyphs]} (view/scene state (q/width) (q/height)
                                                        (q/text-width "M") (q/text-ascent) (q/text-descent))]
    (apply q/background background)
    (q/no-stroke)
    (q/text-align :left :top)
    (doseq [{:keys [x y w h color]} rects]
      (q/fill color)
      (q/rect x y w h))
    (doseq [{:keys [text x y color]} glyphs]
      (q/fill color)
      (q/text text x y)))
  state)
