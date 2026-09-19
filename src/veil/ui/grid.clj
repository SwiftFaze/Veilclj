(ns veil.ui.grid
  "Grid size calculation: how many whole cells fit the window, with 80x24 minimum.")

(defn size
  "Calculate grid size from window dimensions and cell dimensions.
   Returns {:cols :rows} as whole cells, minimum 80 columns by 24 rows."
  [win-w win-h cell-w cell-h]
  {:cols (max 80 (quot win-w cell-w))
   :rows (max 24 (quot win-h cell-h))})

(defn cell-size
  "Calculate cell dimensions from text metrics.
   text-w is the pixel width of a single character (via Quil text-width).
   ascent and descent are the font metrics from Quil.
   Returns {:w :h} as ints (ceiling of the floating metrics)."
  [text-w ascent descent]
  {:w (int (Math/ceil text-w))
   :h (int (Math/ceil (+ ascent descent)))})
