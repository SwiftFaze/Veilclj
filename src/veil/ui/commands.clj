(ns veil.ui.commands
  "Turn a buffer into draw commands: pixel positions and RGB colors.
   Requires veil.game.theme to resolve color keys."
  (:require [veil.game.theme :as theme]
            [veil.ui.buffer :as buffer]))

(defn- placed-cells
  "Every cell of buf with its top-left pixel position (:x, :y), row by row."
  [buf cell-w cell-h]
  (for [row (range (:rows buf))
        col (range (:cols buf))]
    (assoc (buffer/cell buf col row) :x (* col cell-w) :y (* row cell-h))))

(defn frame
  "Turn a buffer and game state into draw commands.
   Returns {:background [r g b] :rects [...] :glyphs [...]}.
   - :background is the theme's BACKGROUND color.
   - Each cell with bg != BACKGROUND gets a rect at its pixel position.
   - Each cell with a non-space glyph gets a glyph command.
   - Color keys are resolved through theme/color (throws if unknown)."
  [state buf cell-w cell-h]
  (let [cells (placed-cells buf cell-w cell-h)]
    {:background (theme/color state :BACKGROUND)
     :rects (vec (for [{:keys [x y bg]} cells
                       :when (not= bg :BACKGROUND)]
                   {:x x :y y :w cell-w :h cell-h :color (theme/color state bg)}))
     :glyphs (vec (for [{:keys [x y glyph fg]} cells
                        :when (not= glyph \space)]
                    {:text (str glyph) :x x :y y :color (theme/color state fg)}))}))
