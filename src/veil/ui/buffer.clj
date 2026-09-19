(ns veil.ui.buffer
  "A buffer is a grid of cells, each containing a glyph and foreground/background colors.
   Pure operations: blank, read, write-text, fill-rect, draw-box.")

(def blank-cell
  "A space glyph in NORMAL_TEXT on BACKGROUND."
  {:glyph \space :fg :NORMAL_TEXT :bg :BACKGROUND})

(def ^:private box-glyphs
  "The single-line box-drawing characters (U+2500 block) a box is made of."
  {:top-left     (char 0x250C)
   :top-right    (char 0x2510)
   :bottom-left  (char 0x2514)
   :bottom-right (char 0x2518)
   :horizontal   (char 0x2500)
   :vertical     (char 0x2502)})

(defn blank
  "Create a buffer of cols by rows blank cells."
  [cols rows]
  {:cols cols
   :rows rows
   :cells (vec (for [_ (range rows)]
                 (vec (repeat cols blank-cell))))})

(defn- within?
  "True when index n is a valid position along an axis of the given size."
  [size n]
  (and (>= n 0) (< n size)))

(defn- in-bounds? [buf col row]
  (and (within? (:cols buf) col)
       (within? (:rows buf) row)))

(defn cell
  "Get the cell at column col, row row, or nil if out of range."
  [buf col row]
  (when (in-bounds? buf col row)
    (get-in buf [:cells row col])))

(defn- put-all
  "buf with each [col row cell-value] placed; a placement outside the grid is dropped."
  [buf placements]
  (reduce (fn [b [col row cell-value]]
            (if (in-bounds? b col row)
              (assoc-in b [:cells row col] cell-value)
              b))
          buf
          placements))

(defn row-text
  "Get the glyphs in a row as a string, or empty string if out of range."
  [buf row]
  (if (within? (:rows buf) row)
    (apply str (map :glyph (get-in buf [:cells row])))
    ""))

(defn write-text
  "Write text into the buffer at column col, row row in the given colors.
   Text that runs past the edges is clipped. Returns a new buffer."
  [buf col row text fg bg]
  (put-all buf (map-indexed (fn [i ch] [(+ col i) row {:glyph ch :fg fg :bg bg}])
                            text)))

(defn fill-rect
  "Fill a rectangle at column col, row row with width w and height h with a background color.
   Returns a new buffer."
  [buf col row w h bg]
  (put-all buf (for [r (range row (+ row h))
                     c (range col (+ col w))]
                 [c r {:glyph \space :fg :NORMAL_TEXT :bg bg}])))

(defn- box-outline
  "[col row glyph] for every cell on the edges of the rectangle at column col,
   row row, w wide and h high: four corners, then the horizontal and vertical edges."
  [col row w h]
  (let [right (+ col (dec w))
        bottom (+ row (dec h))
        across (range (inc col) right)
        down (range (inc row) bottom)]
    (concat [[col row (:top-left box-glyphs)]
             [right row (:top-right box-glyphs)]
             [col bottom (:bottom-left box-glyphs)]
             [right bottom (:bottom-right box-glyphs)]]
            (for [c across, r [row bottom]] [c r (:horizontal box-glyphs)])
            (for [r down, c [col right]] [c r (:vertical box-glyphs)]))))

(defn draw-box
  "Draw a single-line box on the rectangle's edges at column col, row row
   with width w and height h in the given colors. Returns a new buffer."
  [buf col row w h fg bg]
  (if (or (< w 2) (< h 2))
    buf
    (put-all buf (for [[c r glyph] (box-outline col row w h)]
                   [c r {:glyph glyph :fg fg :bg bg}]))))
