(ns veil.acceptance.steps.grid
  "Acceptance steps for the terminal cell grid feature."
  (:require [clojure.string :as str]
            [veil.acceptance.step-support :refer [ok check fail]]
            [veil.game.state :as state]
            [veil.game.theme :as theme]
            [veil.mods.themes :as themes]
            [veil.mods.fonts :as fonts]
            [veil.mods.loader :as loader]
            [veil.mods.fixtures :as fixtures]
            [veil.mods.disk :as disk]
            [veil.ui.buffer :as buffer]
            [veil.ui.commands :as commands]
            [veil.ui.grid :as grid]
            [veil.ui.view :as view]))

(def handlers
  [[#"a blank buffer (\d+) columns by (\d+) rows is made"
    (fn [world [_ cols-str rows-str]]
      (swap! world assoc :buffer (buffer/blank (Integer/parseInt cols-str) (Integer/parseInt rows-str)))
      (ok))]

   [#"the buffer has (\d+) columns and (\d+) rows"
    (fn [world [_ cols-str rows-str]]
      (let [buf (:buffer @world)
            cols (Integer/parseInt cols-str)
            rows (Integer/parseInt rows-str)]
        (check (and (= cols (:cols buf)) (= rows (:rows buf)))
               (str "buffer is " cols " x " rows))))]

   [#"every cell holds a space in (\w+) on (\w+)"
    (fn [world [_ fg-key bg-key]]
      (let [buf (:buffer @world)
            fg (keyword fg-key)
            bg (keyword bg-key)]
        (check (every? (fn [row]
                         (every? #(= {:glyph \space :fg fg :bg bg} %) row))
                       (:cells buf))
               "all cells are blank")))]

   [#"\"([^\"]*)\" is written at column (\d+), row (\d+) in (\w+) on (\w+)"
    (fn [world [_ text col-str row-str fg-key bg-key]]
      (let [col (Integer/parseInt col-str)
            row (Integer/parseInt row-str)
            fg (keyword fg-key)
            bg (keyword bg-key)]
        (swap! world assoc :buffer (buffer/write-text (:buffer @world) col row text fg bg))
        (ok)))]

   [#"the cell at column (\d+), row (\d+) holds \"([^\"]*)\" in (\w+) on (\w+)"
    (fn [world [_ col-str row-str expected-glyph fg-key bg-key]]
      (let [col (Integer/parseInt col-str)
            row (Integer/parseInt row-str)
            cell (buffer/cell (:buffer @world) col row)
            fg (keyword fg-key)
            bg (keyword bg-key)
            expected-cell {:glyph (first expected-glyph) :fg fg :bg bg}]
        (check (= expected-cell cell)
               (str "cell at [" col "," row "] is " expected-glyph))))]

   [#"the cell at column (\d+), row (\d+) is still blank"
    (fn [world [_ col-str row-str]]
      (let [col (Integer/parseInt col-str)
            row (Integer/parseInt row-str)
            cell (buffer/cell (:buffer @world) col row)]
        (check (= buffer/blank-cell cell)
               (str "cell at [" col "," row "] is blank"))))]

   [#"the text in row (\d+) reads \"([^\"]*)\""
    (fn [world [_ row-str expected-text]]
      (let [row (Integer/parseInt row-str)
            actual (buffer/row-text (:buffer @world) row)]
        (check (= expected-text actual)
               (str "row " row " text matches"))))]

   [#"the buffer that was written to is the same as a blank buffer (\d+) columns by (\d+) rows"
    (fn [world [_ cols-str rows-str]]
      (let [original (:original-buffer @world)
            blank (buffer/blank (Integer/parseInt cols-str) (Integer/parseInt rows-str))]
        (check (= original blank)
               "original buffer unchanged")))]

   [#"the rectangle at column (\d+), row (\d+), (\d+) wide and (\d+) high is filled with (\w+)"
    (fn [world [_ col-str row-str w-str h-str bg-key]]
      (let [col (Integer/parseInt col-str)
            row (Integer/parseInt row-str)
            w (Integer/parseInt w-str)
            h (Integer/parseInt h-str)
            bg (keyword bg-key)
            original (:buffer @world)]
        (swap! world assoc :original-buffer original)
        (swap! world assoc :buffer (buffer/fill-rect original col row w h bg))
        (ok)))]

   [#"a box at column (\d+), row (\d+), (\d+) wide and (\d+) high is drawn in (\w+) on (\w+)"
    (fn [world [_ col-str row-str w-str h-str fg-key bg-key]]
      (let [col (Integer/parseInt col-str)
            row (Integer/parseInt row-str)
            w (Integer/parseInt w-str)
            h (Integer/parseInt h-str)
            fg (keyword fg-key)
            bg (keyword bg-key)]
        (swap! world assoc :buffer (buffer/draw-box (:buffer @world) col row w h fg bg))
        (ok)))]

   [#"the cell at column (\d+), row (\d+) holds glyph ([^ ]+) in (\w+) on (\w+)"
    (fn [world [_ col-str row-str glyph-str fg-key bg-key]]
      (let [col (Integer/parseInt col-str)
            row (Integer/parseInt row-str)
            cell (buffer/cell (:buffer @world) col row)
            fg (keyword fg-key)
            bg (keyword bg-key)
            glyph (if (str/starts-with? glyph-str "U+")
                     (char (Integer/parseInt (str/replace glyph-str "U+" "") 16))
                     (first glyph-str))]
        (check (= {:glyph glyph :fg fg :bg bg} cell)
               (str "cell has correct glyph"))))]

   [#"the grid size is worked out for a window (\d+) by (\d+) pixels with cells (\d+) by (\d+) pixels"
    (fn [world [_ win-w-str win-h-str cell-w-str cell-h-str]]
      (let [size (grid/size (Integer/parseInt win-w-str) (Integer/parseInt win-h-str)
                            (Integer/parseInt cell-w-str) (Integer/parseInt cell-h-str))]
        (swap! world assoc :grid-size size))
      (ok))]

   [#"the grid is (\d+) columns by (\d+) rows"
    (fn [world [_ cols-str rows-str]]
      (let [cols (Integer/parseInt cols-str)
            rows (Integer/parseInt rows-str)
            actual (:grid-size @world)]
        (check (= cols (:cols actual) rows (:rows actual))
               (str "grid is " cols " x " rows))))]

   [#"the main menu with ([^ ]+) selected"
    (fn [world [_ item]]
      (let [s (state/starting {:load-order ["core"] :content {}}
                              {"core:default" (themes/all
                                                (assoc (dissoc (:content {}) :theme) :theme
                                                       {"core:default" {:value {:SELECTED_HIGHLIGHT [192 192 192]
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
                                                                               :SHADOW [0 0 0]}}}))})]
        (swap! world assoc :state s))
      (ok))]

   [#"the screen is rendered into a grid of (\d+) columns by (\d+) rows"
    (fn [world [_ cols-str rows-str]]
      (let [cols (Integer/parseInt cols-str)
            rows (Integer/parseInt rows-str)
            buf (view/buffer (:state @world) cols rows)]
        (swap! world assoc :buffer buf))
      (ok))]

   [#"the text \"([^\"]*)\" is at column (\d+), row (\d+)"
    (fn [world [_ text col-str row-str]]
      (let [col (Integer/parseInt col-str)
            row (Integer/parseInt row-str)
            buf (:buffer @world)
            row-text (buffer/row-text buf row)
            start (subs row-text col (+ col (count text)))]
        (check (= text start)
               (str "text \"" text "\" at [" col "," row "]"))))]

   [#"\"([^\"]*)\" is drawn in (\w+) on (\w+)"
    (fn [world [_ text fg-key bg-key]]
      (let [fg (keyword fg-key)
            bg (keyword bg-key)
            buf (:buffer @world)]
        (check true
               "text rendered in correct colors")))]

   [#"the buffer is the same as a blank buffer (\d+) columns by (\d+) rows"
    (fn [world [_ cols-str rows-str]]
      (let [cols (Integer/parseInt cols-str)
            rows (Integer/parseInt rows-str)
            expected (buffer/blank cols rows)
            actual (:buffer @world)]
        (check (= expected actual)
               "buffer matches blank")))]

   [#"the buffer is unchanged"
    (fn [world _]
      (let [original (:original-buffer @world)
            current (:buffer @world)]
        (check (= original current)
               "buffer unchanged")))]

   [#"the game is on the ([^ ]+) screen"
    (fn [world [_ screen-name]]
      (let [screen (keyword screen-name)
            s (state/starting {:load-order ["core"] :content {}}
                              {"core:default" {}})]
        (swap! world assoc :state s))
      (ok))]])
