(ns veil.ui.buffer
  "A buffer is a grid of cells, each containing a glyph and foreground/background colors.
   Pure operations: blank, read, write-text, fill-rect, draw-box.")

(def blank-cell
  "A space glyph in NORMAL_TEXT on BACKGROUND."
  {:glyph \space :fg :NORMAL_TEXT :bg :BACKGROUND})

(defn blank
  "Create a buffer of cols by rows blank cells."
  [cols rows]
  {:cols cols
   :rows rows
   :cells (vec (for [_ (range rows)]
                 (vec (repeat cols blank-cell))))})

(defn cell
  "Get the cell at column col, row row, or nil if out of range."
  [buf col row]
  (when (and (>= col 0) (< col (:cols buf))
             (>= row 0) (< row (:rows buf)))
    (get-in buf [:cells row col])))

(defn- put
  "buf with the cell-value at col,row set, or buf itself when that cell is outside the grid."
  [buf col row cell-value]
  (if (cell buf col row)
    (assoc-in buf [:cells row col] cell-value)
    buf))

(defn row-text
  "Get the glyphs in a row as a string, or empty string if out of range."
  [buf row]
  (if (and (>= row 0) (< row (:rows buf)))
    (apply str (map :glyph (get-in buf [:cells row])))
    ""))

(defn write-text
  "Write text into the buffer at column col, row row in the given colors.
   Text that runs past the edges is clipped. Returns a new buffer."
  [buf col row text fg bg]
  (reduce (fn [b [i ch]]
            (put b (+ col i) row {:glyph ch :fg fg :bg bg}))
          buf
          (map-indexed vector text)))

(defn fill-rect
  "Fill a rectangle at column col, row row with width w and height h with a background color.
   Returns a new buffer."
  [buf col row w h bg]
  (reduce (fn [b [c r]]
            (put b c r {:glyph \space :fg :NORMAL_TEXT :bg bg}))
          buf
          (for [r (range row (+ row h)) c (range col (+ col w))]
            [c r])))

(defn draw-box
  "Draw a single-line box on the rectangle's edges at column col, row row
   with width w and height h in the given colors. Returns a new buffer."
  [buf col row w h fg bg]
  (if (or (< w 2) (< h 2))
    buf
    (let [right (+ col (dec w))
          bottom (+ row (dec h))
          box-cells (concat
                      ;; Four corners
                      [[col row (char 0x250C)]
                       [right row (char 0x2510)]
                       [col bottom (char 0x2514)]
                       [right bottom (char 0x2518)]]
                      ;; Top and bottom edges
                      (map (fn [c] [c row (char 0x2500)]) (range (inc col) right))
                      (map (fn [c] [c bottom (char 0x2500)]) (range (inc col) right))
                      ;; Left and right edges
                      (map (fn [r] [col r (char 0x2502)]) (range (inc row) bottom))
                      (map (fn [r] [right r (char 0x2502)]) (range (inc row) bottom)))]
      (reduce (fn [b [c r cp]]
                (put b c r {:glyph cp :fg fg :bg bg}))
              buf
              box-cells))))
