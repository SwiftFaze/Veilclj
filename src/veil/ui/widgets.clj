(ns veil.ui.widgets
  "Pure widget functions that write into a buffer: frames, bars, keycaps, hints, etc.
   All are pure functions over a cell buffer; none touch Quil or state."
  (:require [veil.ui.buffer :as buffer]))

;; --- Frame ---

(defn frame
  "Draw a titled frame with an optional title in the top edge.
   If title is nil, draws an untitled box (same as draw-box).
   Title starts 3 cells in; at most w-6 chars of it are shown, cut with no ellipsis.
   For w <= 6, frame is untitled regardless."
  [buf col row w h title fg bg]
  (if (or (nil? title) (<= w 6))
    (buffer/draw-box buf col row w h fg bg)
    (let [max-title-len (max 0 (- w 6))
          title-text (subs title 0 (min (count title) max-title-len))
          top-left (char 0x250C)
          top-right (char 0x2510)
          horizontal (char 0x2500)
          vertical (char 0x2502)
          bottom-left (char 0x2514)
          bottom-right (char 0x2518)
          right (+ col (dec w))
          bottom (+ row (dec h))
          title-start (+ col 3)
          title-end (+ title-start (count title-text))

          placements (vec
            (concat
              [[col row top-left] [right row top-right]
               [col bottom bottom-left] [right bottom bottom-right]]
              (for [r (range (inc row) bottom)]
                [col r vertical])
              (for [r (range (inc row) bottom)]
                [right r vertical])
              (for [c (range (inc col) right)]
                [c bottom horizontal])
              (for [c (range (inc col) (+ col 2))]
                [c row horizontal])
              [[(+ col 2) row \space]]
              (for [[i ch] (map-indexed vector title-text)]
                [(+ title-start i) row ch])
              [[title-end row \space]]
              (for [c (range (+ title-end 1) right)]
                [c row horizontal])))]

      (reduce (fn [b [c r glyph]]
                (if (and (>= c 0) (< c (:cols b)) (>= r 0) (< r (:rows b)))
                  (assoc-in b [:cells r c] {:glyph glyph :fg fg :bg bg})
                  b))
              buf
              placements))))

;; --- Title bar ---

(defn title-bar
  "Draw a full-width SELECTED_TEXT on SELECTED_HIGHLIGHT row.
   Left text at col 1, center text centered."
  [buf left center row]
  (let [cols (:cols buf)
        filled (buffer/fill-rect buf 0 row cols 1 :SELECTED_HIGHLIGHT)
        with-left (buffer/write-text filled 1 row left :SELECTED_TEXT :SELECTED_HIGHLIGHT)
        center-col (quot (- cols (count center)) 2)
        with-center (buffer/write-text with-left center-col row center :SELECTED_TEXT :SELECTED_HIGHLIGHT)]
    with-center))

;; --- Status line ---

(defn status-line
  "Draw a centered bracketed message in reverse video, or leave the row blank if no message."
  [buf message row]
  (if (or (nil? message) (empty? message))
    buf
    (let [cols (:cols buf)
          bracketed (str "[ " message " ]")
          col (quot (- cols (count bracketed)) 2)]
      (buffer/write-text buf col row bracketed :SELECTED_TEXT :SELECTED_HIGHLIGHT))))

;; --- Keycap label lookup ---

(defn keycap-label
  "Pure input -> string lookup for key labels."
  [input]
  (cond
    (= input :back) "Esc"
    (= input :confirm) "Enter"
    (= input :tab) "Tab"
    (= input :up) "Up"
    (= input :f12) "F12"
    (map? input)
    (let [{:keys [char mods]} input]
      (if (seq mods)
        (str "^" (Character/toUpperCase char))
        (str (Character/toUpperCase char))))
    :else "?"))

;; --- Keycap ---

(defn keycap
  "Write a keycap in reverse video at col, row."
  [buf input col row]
  (let [label (keycap-label input)]
    (buffer/write-text buf col row label :SELECTED_TEXT :SELECTED_HIGHLIGHT)))

;; --- Hint bar ---

(defn hint-bar-rows
  "Calculate how many rows a hint bar with these hints would take."
  [buf hints]
  (if (empty? hints)
    0
    (let [cols (:cols buf)
          slot-width
          (+ 1 (apply max (map (fn [[input label]]
                                 (+ (count (keycap-label input)) 1 (count label)))
                               hints)))
          slots-per-row (max 1 (quot cols slot-width))
          num-hints (count hints)
          bar-rows (quot (+ num-hints slots-per-row (dec 1)) slots-per-row)]
      bar-rows)))

(defn hint-bar
  "Draw a hint bar: ordered seq of [input label] pairs, laid out in equal slots,
   wrapping into rows docked at the bottom. Returns the buffer with hint bar drawn."
  [buf hints]
  (if (empty? hints)
    buf
    (let [cols (:cols buf)
          rows (:rows buf)
          slot-width
          (+ 1 (apply max (map (fn [[input label]]
                                 (+ (count (keycap-label input)) 1 (count label)))
                               hints)))
          slots-per-row (max 1 (quot cols slot-width))
          num-hints (count hints)
          bar-rows (quot (+ num-hints slots-per-row (dec 1)) slots-per-row)
          bar-start-row (- rows bar-rows)]

      (reduce (fn [b [hint-idx [input label]]]
                (let [slot-idx hint-idx
                      row-offset (quot slot-idx slots-per-row)
                      col-offset (mod slot-idx slots-per-row)
                      hint-row (+ bar-start-row row-offset)
                      hint-col (* col-offset slot-width)
                      keycap-label-text (keycap-label input)
                      with-cap (buffer/write-text b hint-col hint-row keycap-label-text
                                                  :SELECTED_TEXT :SELECTED_HIGHLIGHT)
                      label-col (+ hint-col (count keycap-label-text) 1)]
                  (buffer/write-text with-cap label-col hint-row label
                                     :NORMAL_TEXT :BACKGROUND)))
              buf
              (map-indexed (fn [i hint] [i hint]) hints)))))

;; --- Accelerator label ---

(defn accelerator-label
  "Write a label where the first case-insensitive occurrence of letter is highlighted in ACCENT."
  [buf label letter col row fg bg]
  (let [lower-letter (Character/toLowerCase letter)]
    (reduce (fn [b [idx ch]]
              (let [is-accelerator (= (Character/toLowerCase ch) lower-letter)
                    [c fg-to-use]
                    (if is-accelerator
                      [ch :ACCENT]
                      [ch fg])]
                (buffer/write-text b (+ col idx) row (str c) fg-to-use bg)))
            buf
            (map-indexed (fn [i ch] [i ch]) label))))

;; --- Badge ---

(defn badge
  "Write a badge: ' text ' in BACKGROUND on color."
  [buf color text col row]
  (let [badge-text (str " " text " ")]
    (buffer/write-text buf col row badge-text :BACKGROUND color)))

;; --- Chip ---

(defn chip
  "Write a chip: '[tag]' in color on BACKGROUND, or BACKGROUND on color if focused."
  [buf color tag col row focused?]
  (let [chip-text (str "[" tag "]")
        [fg bg] (if focused?
                  [:BACKGROUND color]
                  [color :BACKGROUND])]
    (buffer/write-text buf col row chip-text fg bg)))
