(ns veil.acceptance.steps.grid
  "Acceptance steps for the terminal cell grid feature, and for the themes
  scenarios that observe the menu through the buffer. They drive the pure
  veil.ui code (buffer, grid, commands, view), never a window."
  (:require [clojure.string :as str]
            [veil.acceptance.step-support :refer [ok check fail]]
            [veil.game.state :as state]
            [veil.game.theme :as theme]
            [veil.ui.buffer :as buffer]
            [veil.ui.commands :as commands]
            [veil.ui.grid :as grid]
            [veil.ui.view :as view]))

;; --- Shared data and helpers ---

(def ^:private default-colors
  "core:default as shipped: the 13 required keys and the 6 optional ones resolved."
  {:SELECTED_HIGHLIGHT [192 192 192]
   :SELECTED_TEXT [0 0 0]
   :NORMAL_TEXT [255 255 255]
   :DIMMED_TEXT [128 128 128]
   :BACKGROUND [0 0 0]
   :INVALID_HIGHLIGHT [224 90 78]
   :VALID_HIGHLIGHT [111 207 125]
   :TABLE_HEADER_BACKGROUND [26 26 26]
   :BORDER [192 192 192]
   :SCROLLBAR_THUMB [128 128 128]
   :ACCENT [238 179 146]
   :WINDOW_BORDER [255 255 255]
   :TABLE_HEADER_TEXT [0 194 194]
   :SUCCESS [111 207 125]
   :ERROR [224 90 78]
   :WARNING [238 179 146]
   :INFO [238 179 146]
   :FOCUSED_BORDER [238 179 146]
   :SHADOW [0 0 0]})

(defn- default-state []
  (state/starting {:load-order ["core"] :content {}}
                  {theme/default-id default-colors}))

(defn- current-state
  "The world's game state, or the default themed state when the scenario named none."
  [world]
  (or (:state @world) (default-state)))

(defn- int-of [s]
  (Long/parseLong s))

(defn- rgb-of [r g b]
  [(int-of r) (int-of g) (int-of b)])

(defn- current-buffer [world]
  (:buffer @world))

(defn- guarded
  "Wrap a handler so a bad input or a broken step becomes a failed step and
  never an exception that would abort the whole generated test."
  [handler]
  (fn [world groups]
    (try
      (handler world groups)
      (catch Throwable t
        (fail (str "step raised " (.getSimpleName (class t)) ": " (.getMessage t)))))))

(defn- with-buffer
  "Run (f buf) when the world has a buffer, else fail saying so."
  [world f]
  (if-let [buf (current-buffer world)]
    (f buf)
    (fail "no buffer has been made in this scenario")))

(defn- with-frame
  "Run (f frame) when draw commands were built, else fail with why not."
  [world f]
  (if-let [frame (:frame @world)]
    (f frame)
    (fail (str "no draw commands were built"
               (when-let [error (:commands-error @world)]
                 (str " (building them failed: " error ")"))))))

(defn- update-buffer!
  "Store the result of (f buffer) as the buffer, keeping the one written to."
  [world f]
  (with-buffer world
    (fn [buf]
      (swap! world assoc :original-buffer buf :buffer (f buf))
      (ok))))

(defn- cell-is [world col row expected description]
  (with-buffer world
    (fn [buf]
      (let [actual (buffer/cell buf col row)]
        (check (= expected actual)
               (str "cell at column " col ", row " row " " description ", got " (pr-str actual)))))))

(defn- find-text
  "[col row] of the first place text appears in a row of buf, or nil."
  [buf text]
  (first (for [row (range (:rows buf))
               :let [col (str/index-of (buffer/row-text buf row) text)]
               :when col]
           [col row])))

(defn- with-text-location
  "Run (f buf col row) for the place text is drawn in the buffer, else fail."
  [world text f]
  (with-buffer world
    (fn [buf]
      (if-let [[col row] (find-text buf text)]
        (f buf col row)
        (fail (str "\"" text "\" is not drawn anywhere in the buffer"))))))

(defn- cells-of
  "The cells a text occupies once found at col, row."
  [buf text col row]
  (map #(buffer/cell buf % row) (range col (+ col (count text)))))

(defn- span-glyphs
  "The glyph commands drawn over the n cells from col on row, for cell size [cw ch]."
  [frame [cw ch] col row n]
  (filter (fn [{:keys [x y]}]
            (and (= y (* row ch))
                 (<= (* col cw) x)
                 (< x (* (+ col n) cw))))
          (:glyphs frame)))

(defn- have-color
  "Check that some draw commands were found and all of them have the RGB color;
  label names them in the failure message."
  [found label rgb]
  (check (and (seq found) (every? #(= rgb (:color %)) found))
         (str label " have colors " (pr-str (map :color found)) ", expected " rgb)))

(defn- build-commands!
  "Turn the world's buffer into draw commands for the given cell size. A
  failure is kept in the world, so a scenario can assert it."
  [world buf cell-w cell-h]
  (swap! world update :buffer-snapshot #(or % buf))
  (let [built (try (commands/frame (current-state world) buf cell-w cell-h)
                   (catch Exception e {:error (.getMessage e)}))]
    (if-let [error (:error built)]
      (swap! world #(-> % (assoc :commands-error error) (dissoc :frame)))
      (swap! world #(-> % (assoc :frame built :cell-size [cell-w cell-h])
                        (dissoc :commands-error))))
    (ok)))

;; --- Handlers ---

(def ^:private raw-handlers
  [;; The buffer
   [#"a blank buffer (\d+) columns? by (\d+) rows?(?: is made)?"
    (fn [world [_ cols rows]]
      (swap! world #(-> % (assoc :buffer (buffer/blank (int-of cols) (int-of rows)))
                        (dissoc :original-buffer :buffer-snapshot :frame :commands-error)))
      (ok))]

   [#"the buffer (?:still )?has (\d+) columns? and (\d+) rows?"
    (fn [world [_ cols rows]]
      (with-buffer world
        (fn [buf]
          (check (= [(int-of cols) (int-of rows)] [(:cols buf) (:rows buf)])
                 (str "buffer is " (:cols buf) " by " (:rows buf) ", expected " cols " by " rows)))))]

   [#"every cell holds a space in (\w+) on (\w+)"
    (fn [world [_ fg bg]]
      (with-buffer world
        (fn [buf]
          (let [expected {:glyph \space :fg (keyword fg) :bg (keyword bg)}]
            (check (every? #(= expected %) (apply concat (:cells buf)))
                   (str "not every cell holds a space in " fg " on " bg))))))]

   [#"\"([^\"]*)\" is written at column (-?\d+), row (-?\d+) in (\w+) on (\w+)"
    (fn [world [_ text col row fg bg]]
      (update-buffer! world #(buffer/write-text % (int-of col) (int-of row) text
                                                (keyword fg) (keyword bg))))]

   [#"the rectangle at column (-?\d+), row (-?\d+), (\d+) wide and (\d+) high is filled with (\w+)"
    (fn [world [_ col row w h bg]]
      (update-buffer! world #(buffer/fill-rect % (int-of col) (int-of row) (int-of w) (int-of h)
                                               (keyword bg))))]

   [#"a box at column (-?\d+), row (-?\d+), (\d+) wide and (\d+) high is drawn in (\w+) on (\w+)"
    (fn [world [_ col row w h fg bg]]
      (update-buffer! world #(buffer/draw-box % (int-of col) (int-of row) (int-of w) (int-of h)
                                              (keyword fg) (keyword bg))))]

   [#"the cell at column (-?\d+), row (-?\d+) holds \"([^\"])\" in (\w+) on (\w+)"
    (fn [world [_ col row glyph fg bg]]
      (cell-is world (int-of col) (int-of row)
               {:glyph (first glyph) :fg (keyword fg) :bg (keyword bg)}
               (str "holds \"" glyph "\" in " fg " on " bg)))]

   [#"the cell at column (-?\d+), row (-?\d+) holds a space in (\w+) on (\w+)"
    (fn [world [_ col row fg bg]]
      (cell-is world (int-of col) (int-of row)
               {:glyph \space :fg (keyword fg) :bg (keyword bg)}
               (str "holds a space in " fg " on " bg)))]

   [#"the cell at column (-?\d+), row (-?\d+) holds glyph U\+([0-9A-Fa-f]+) in (\w+) on (\w+)"
    (fn [world [_ col row code fg bg]]
      (cell-is world (int-of col) (int-of row)
               {:glyph (char (Integer/parseInt code 16)) :fg (keyword fg) :bg (keyword bg)}
               (str "holds glyph U+" code " in " fg " on " bg)))]

   [#"the cell at column (-?\d+), row (-?\d+) is still blank"
    (fn [world [_ col row]]
      (cell-is world (int-of col) (int-of row) buffer/blank-cell "is still blank"))]

   [#"the cell at column (-?\d+), row (-?\d+) has the background (\w+)"
    (fn [world [_ col row bg]]
      (with-buffer world
        (fn [buf]
          (let [actual (:bg (buffer/cell buf (int-of col) (int-of row)))]
            (check (= (keyword bg) actual)
                   (str "cell at column " col ", row " row " has background " actual
                        ", expected " bg))))))]

   [#"the text in row (\d+) reads \"([^\"]*)\""
    (fn [world [_ row expected]]
      (with-buffer world
        (fn [buf]
          (let [actual (buffer/row-text buf (int-of row))]
            (check (= expected actual)
                   (str "row " row " reads \"" actual "\", expected \"" expected "\""))))))]

   [#"the buffer is the same as a blank buffer (\d+) columns? by (\d+) rows?"
    (fn [world [_ cols rows]]
      (with-buffer world
        (fn [buf]
          (check (= (buffer/blank (int-of cols) (int-of rows)) buf)
                 "the buffer is not the same as a blank buffer of that size"))))]

   [#"the buffer that was written to is the same as a blank buffer (\d+) columns? by (\d+) rows?"
    (fn [world [_ cols rows]]
      (if-let [original (:original-buffer @world)]
        (check (= (buffer/blank (int-of cols) (int-of rows)) original)
               "the buffer that was written to was changed by the write")
        (fail "nothing was written in this scenario")))]

   ;; The grid size
   [#"the grid size is worked out for a window (\d+) by (\d+) pixels with cells (\d+) by (\d+) pixels"
    (fn [world [_ win-w win-h cell-w cell-h]]
      (swap! world assoc :grid-size (grid/size (int-of win-w) (int-of win-h)
                                               (int-of cell-w) (int-of cell-h)))
      (ok))]

   [#"the grid is (\d+) columns by (\d+) rows"
    (fn [world [_ cols rows]]
      (let [actual (:grid-size @world)]
        (check (= {:cols (int-of cols) :rows (int-of rows)} actual)
               (str "grid is " (pr-str actual) ", expected " cols " columns by " rows " rows"))))]

   ;; The theme
   [#"the active theme has (.+)"
    (fn [world [_ pairs]]
      (let [colors (re-seq #"([A-Z_]+) (\d+),(\d+),(\d+)" pairs)
            state (current-state world)]
        (if (seq colors)
          (do (swap! world assoc :state
                     (reduce (fn [s [_ k r g b]]
                               (assoc-in s [:themes (:active-theme s) (keyword k)] (rgb-of r g b)))
                             state colors))
              (ok))
          (fail (str "no colors in \"" pairs "\"")))))]

   [#"the theme's ([A-Z_]+) is changed to (\d+),(\d+),(\d+)"
    (fn [world [_ k r g b]]
      (let [state (current-state world)]
        (swap! world assoc :state
               (assoc-in state [:themes (:active-theme state) (keyword k)] (rgb-of r g b)))
        (ok)))]

   ;; The screens
   [#"the screen is rendered into a grid of (\d+) columns by (\d+) rows"
    (fn [world [_ cols rows]]
      (if-let [s (:state @world)]
        (do (swap! world #(-> % (assoc :buffer (view/buffer s (int-of cols) (int-of rows)))
                              (dissoc :original-buffer :buffer-snapshot :frame :commands-error)))
            (ok))
        (fail "there is no game state to render")))]

   [#"the text \"([^\"]*)\" is at column (\d+), row (\d+)"
    (fn [world [_ text col row]]
      (with-buffer world
        (fn [buf]
          (let [actual (apply str (map #(:glyph (buffer/cell buf % (int-of row)))
                                       (range (int-of col) (+ (int-of col) (count text)))))]
            (check (= text actual)
                   (str "at column " col ", row " row " the buffer has \"" actual
                        "\", expected \"" text "\""))))))]

   [#"\"([^\"]+)\" is drawn in (\w+) on (\w+)"
    (fn [world [_ text fg bg]]
      (with-text-location world text
        (fn [buf col row]
          (let [cells (cells-of buf text col row)]
            (check (every? #(= [(keyword fg) (keyword bg)] [(:fg %) (:bg %)]) cells)
                   (str "\"" text "\" is drawn in "
                        (pr-str (distinct (map (juxt :fg :bg) cells)))
                        ", expected " fg " on " bg))))))]

   ;; From buffer to draw commands
   [#"the buffer is turned into draw commands for cells (\d+) by (\d+) pixels"
    (fn [world [_ cell-w cell-h]]
      (with-buffer world
        #(build-commands! world % (int-of cell-w) (int-of cell-h))))]

   [#"building the draw commands fails naming the color key \"([^\"]+)\""
    (fn [world [_ key-name]]
      (let [error (:commands-error @world)]
        (check (and error (nil? (:frame @world)) (str/includes? error key-name))
               (str "expected the draw commands to fail naming " key-name
                    ", got error " (pr-str error)))))]

   [#"the frame's background color is (\d+),(\d+),(\d+)"
    (fn [world [_ r g b]]
      (with-frame world
        (fn [frame]
          (check (= (rgb-of r g b) (:background frame))
                 (str "frame's background color is " (:background frame)
                      ", expected " (rgb-of r g b))))))]

   [#"there are no draw commands"
    (fn [world _]
      (with-frame world
        (fn [frame]
          (check (and (empty? (:rects frame)) (empty? (:glyphs frame)))
                 (str "expected no draw commands, got " (count (:rects frame)) " rectangles and "
                      (count (:glyphs frame)) " glyphs")))))]

   [#"there is no glyph command"
    (fn [world _]
      (with-frame world
        (fn [frame]
          (check (empty? (:glyphs frame))
                 (str "expected no glyph command, got " (count (:glyphs frame)))))))]

   [#"there is one rectangle command"
    (fn [world _]
      (with-frame world
        (fn [frame]
          (check (= 1 (count (:rects frame)))
                 (str "expected one rectangle command, got " (count (:rects frame)))))))]

   [#"there is a glyph command for \"([^\"])\" at x (\d+) and y (\d+)"
    (fn [world [_ text x y]]
      (with-frame world
        (fn [frame]
          (check (some #(and (= text (:text %)) (= (int-of x) (:x %)) (= (int-of y) (:y %)))
                       (:glyphs frame))
                 (str "no glyph command for \"" text "\" at x " x " and y " y ", have "
                      (pr-str (:glyphs frame)))))))]

   [#"the glyph command for \"([^\"])\" has the color (\d+),(\d+),(\d+)"
    (fn [world [_ text r g b]]
      (with-frame world
        (fn [frame]
          (have-color (filter #(= text (:text %)) (:glyphs frame))
                      (str "glyph commands for \"" text "\"")
                      (rgb-of r g b)))))]

   [#"there is a rectangle command at x (\d+) and y (\d+), (\d+) wide and (\d+) high, with the color (\d+),(\d+),(\d+)"
    (fn [world [_ x y w h r g b]]
      (with-frame world
        (fn [frame]
          (let [expected {:x (int-of x) :y (int-of y) :w (int-of w) :h (int-of h)
                          :color (rgb-of r g b)}]
            (check (some #(= expected %) (:rects frame))
                   (str "no rectangle command " expected ", have " (pr-str (:rects frame))))))))]

   [#"the rectangle command at x (\d+) and y (\d+) has the color (\d+),(\d+),(\d+)"
    (fn [world [_ x y r g b]]
      (with-frame world
        (fn [frame]
          (have-color (filter #(and (= (int-of x) (:x %)) (= (int-of y) (:y %))) (:rects frame))
                      (str "rectangles at x " x " y " y)
                      (rgb-of r g b)))))]

   [#"the rectangle command behind \"([^\"]+)\" has the color (\d+),(\d+),(\d+)"
    (fn [world [_ text r g b]]
      (with-frame world
        (fn [frame]
          (with-text-location world text
            (fn [_ col row]
              (let [[cw ch] (:cell-size @world)]
                (have-color (filter #(and (= (* col cw) (:x %)) (= (* row ch) (:y %))) (:rects frame))
                            (str "rectangles behind \"" text "\"")
                            (rgb-of r g b))))))))]

   [#"the glyph commands for \"([^\"]+)\" have the color (\d+),(\d+),(\d+)"
    (fn [world [_ text r g b]]
      (with-frame world
        (fn [frame]
          (with-text-location world text
            (fn [_ col row]
              (have-color (span-glyphs frame (:cell-size @world) col row (count text))
                          (str "glyph commands for \"" text "\"")
                          (rgb-of r g b)))))))]

   [#"the buffer is unchanged"
    (fn [world _]
      (with-buffer world
        (fn [buf]
          (let [snapshot (:buffer-snapshot @world)
                cells (apply concat (:cells buf))]
            (check (and (= snapshot buf)
                        (every? #(and (keyword? (:fg %)) (keyword? (:bg %))) cells))
                   "the buffer changed, or holds colors instead of theme keys")))))]])

(def handlers
  (mapv (fn [[pattern handler]] [pattern (guarded handler)]) raw-handlers))
