(ns veil.ui.commands
  "Turn a buffer into draw commands: pixel positions and RGB colors.
   Requires veil.game.theme to resolve color keys."
  (:require [veil.game.theme :as theme]
            [veil.ui.buffer :as buffer]))

(defn frame
  "Turn a buffer and game state into draw commands.
   Returns {:background [r g b] :rects [...] :glyphs [...]}.
   - :background is the theme's BACKGROUND color.
   - Each cell with bg != BACKGROUND gets a rect at its pixel position.
   - Each cell with a non-space glyph gets a glyph command.
   - Color keys are resolved through theme/color (throws if unknown)."
  [state buffer cell-w cell-h]
  (let [background (theme/color state :BACKGROUND)
        rects (vec (for [row (range (:rows buffer))
                         col (range (:cols buffer))
                         :let [cell (buffer/cell buffer col row)
                               cell-bg (:bg cell)]
                         :when (not= cell-bg :BACKGROUND)]
                     {:x (* col cell-w)
                      :y (* row cell-h)
                      :w cell-w
                      :h cell-h
                      :color (theme/color state cell-bg)}))
        glyphs (vec (for [row (range (:rows buffer))
                          col (range (:cols buffer))
                          :let [cell (buffer/cell buffer col row)
                                glyph (:glyph cell)]
                          :when (not= glyph \space)]
                      {:text (str glyph)
                       :x (* col cell-w)
                       :y (* row cell-h)
                       :color (theme/color state (:fg cell))}))]
    {:background background
     :rects rects
     :glyphs glyphs}))
