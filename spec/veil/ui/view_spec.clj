(ns veil.ui.view-spec
  (:require [speclj.core :refer :all]
            [veil.ui.view :as view]
            [veil.ui.buffer :as buffer]
            [veil.game.state :as state]))

(defn- themed-state
  "Build a state with the default theme loaded."
  []
  (state/starting
    {:load-order ["core"] :content {}}
    {"core:default" {:SELECTED_HIGHLIGHT [192 192 192]
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
                      :SHADOW [0 0 0]}}))

(defn- options-screen [s]
  (-> s (state/handle-input :down) (state/handle-input :confirm)))

(defn- map-screen [s]
  (state/handle-input s :confirm))

(defn- text-at
  "The glyphs of n cells of row starting at col."
  [buf col row n]
  (apply str (map #(:glyph (buffer/cell buf % row)) (range col (+ col n)))))

(describe "main menu buffer"
  (it "has the size it was asked for"
    (let [buf (view/buffer (themed-state) 100 30)]
      (should= 100 (:cols buf))
      (should= 30 (:rows buf))))

  (it "centers the title on row 2"
    (should= "VEIL" (text-at (view/buffer (themed-state) 80 24) 38 2 4)))

  (it "lists the menu items on rows 4, 6 and 8, centered"
    (let [buf (view/buffer (themed-state) 80 24)]
      (should= "New Game" (text-at buf 36 4 8))
      (should= "Options" (text-at buf 36 6 7))
      (should= "Quit" (text-at buf 38 8 4))))

  (it "puts the hint on row 12"
    (should= "Use Up/Down or W/S to move, Enter to select"
             (text-at (view/buffer (themed-state) 80 24) 18 12 43)))

  (it "keeps text centered when the grid is wider"
    (let [buf (view/buffer (themed-state) 100 30)]
      (should= "VEIL" (text-at buf 48 2 4))
      (should= "New Game" (text-at buf 46 4 8))))

  (it "draws the selected item in reverse video on exactly its own cells"
    (let [buf (view/buffer (themed-state) 80 24)]
      (should= {:glyph \N :fg :SELECTED_TEXT :bg :SELECTED_HIGHLIGHT} (buffer/cell buf 36 4))
      (should= {:glyph \e :fg :SELECTED_TEXT :bg :SELECTED_HIGHLIGHT} (buffer/cell buf 43 4))
      (should= buffer/blank-cell (buffer/cell buf 35 4))
      (should= buffer/blank-cell (buffer/cell buf 44 4))))

  (it "draws the other items and the title in normal text on the background"
    (let [buf (view/buffer (themed-state) 80 24)]
      (should= {:glyph \O :fg :NORMAL_TEXT :bg :BACKGROUND} (buffer/cell buf 36 6))
      (should= {:glyph \V :fg :NORMAL_TEXT :bg :BACKGROUND} (buffer/cell buf 38 2))))

  (it "moves the reverse video with the selection"
    (let [buf (view/buffer (state/handle-input (themed-state) :down) 80 24)]
      (should= :SELECTED_HIGHLIGHT (:bg (buffer/cell buf 36 6)))
      (should= :BACKGROUND (:bg (buffer/cell buf 36 4))))))

(describe "map screen buffer"
  (it "shows the player as @ and an Esc hint"
    (let [buf (view/buffer (map-screen (themed-state)) 80 24)]
      (should= "@" (text-at buf 39 6 1))
      (should= "Esc: menu" (text-at buf 35 20 9)))))

(describe "options screen buffer"
  (it "shows a heading and an Esc hint"
    (let [buf (view/buffer (options-screen (themed-state)) 80 24)]
      (should= "Options" (text-at buf 36 4 7))
      (should= "Esc: back" (text-at buf 35 20 9))))

  (it "draws the heading in normal text even though Options is the selected menu item"
    (let [s (options-screen (themed-state))
          buf (view/buffer s 80 24)]
      (should= "Options" (state/selected-item s))
      (should= {:glyph \O :fg :NORMAL_TEXT :bg :BACKGROUND} (buffer/cell buf 36 4)))))

(describe "border on main menu"
  (it "draws the top-left corner"
    (let [buf (view/buffer (themed-state) 100 30)]
      (should= {:glyph \┌ :fg :WINDOW_BORDER :bg :BACKGROUND} (buffer/cell buf 0 0))))

  (it "draws the top-right corner"
    (let [buf (view/buffer (themed-state) 100 30)]
      (should= {:glyph \┐ :fg :WINDOW_BORDER :bg :BACKGROUND} (buffer/cell buf 99 0))))

  (it "draws the bottom-left corner"
    (let [buf (view/buffer (themed-state) 100 30)]
      (should= {:glyph \└ :fg :WINDOW_BORDER :bg :BACKGROUND} (buffer/cell buf 0 29))))

  (it "draws the bottom-right corner"
    (let [buf (view/buffer (themed-state) 100 30)]
      (should= {:glyph \┘ :fg :WINDOW_BORDER :bg :BACKGROUND} (buffer/cell buf 99 29))))

  (it "draws the top edge"
    (let [buf (view/buffer (themed-state) 100 30)]
      (should= {:glyph \─ :fg :WINDOW_BORDER :bg :BACKGROUND} (buffer/cell buf 50 0))))

  (it "draws the bottom edge"
    (let [buf (view/buffer (themed-state) 100 30)]
      (should= {:glyph \─ :fg :WINDOW_BORDER :bg :BACKGROUND} (buffer/cell buf 50 29))))

  (it "draws the left edge"
    (let [buf (view/buffer (themed-state) 100 30)]
      (should= {:glyph \│ :fg :WINDOW_BORDER :bg :BACKGROUND} (buffer/cell buf 0 15))))

  (it "draws the right edge"
    (let [buf (view/buffer (themed-state) 100 30)]
      (should= {:glyph \│ :fg :WINDOW_BORDER :bg :BACKGROUND} (buffer/cell buf 99 15))))

  (it "keeps the cell inside the top-left corner blank"
    (let [buf (view/buffer (themed-state) 100 30)]
      (should= buffer/blank-cell (buffer/cell buf 1 1))))

  (it "keeps the cell inside the top-right corner blank"
    (let [buf (view/buffer (themed-state) 100 30)]
      (should= buffer/blank-cell (buffer/cell buf 98 1))))

  (it "keeps the cell inside the bottom-left corner blank"
    (let [buf (view/buffer (themed-state) 100 30)]
      (should= buffer/blank-cell (buffer/cell buf 1 28))))

  (it "keeps the cell inside the bottom-right corner blank"
    (let [buf (view/buffer (themed-state) 100 30)]
      (should= buffer/blank-cell (buffer/cell buf 98 28)))))

(describe "border on map screen"
  (it "draws the top-left corner"
    (let [buf (view/buffer (map-screen (themed-state)) 100 30)]
      (should= {:glyph \┌ :fg :WINDOW_BORDER :bg :BACKGROUND} (buffer/cell buf 0 0))))

  (it "draws the bottom-right corner"
    (let [buf (view/buffer (map-screen (themed-state)) 100 30)]
      (should= {:glyph \┘ :fg :WINDOW_BORDER :bg :BACKGROUND} (buffer/cell buf 99 29)))))

(describe "border on options screen"
  (it "draws the top-left corner"
    (let [buf (view/buffer (options-screen (themed-state)) 100 30)]
      (should= {:glyph \┌ :fg :WINDOW_BORDER :bg :BACKGROUND} (buffer/cell buf 0 0))))

  (it "draws the bottom-right corner"
    (let [buf (view/buffer (options-screen (themed-state)) 100 30)]
      (should= {:glyph \┘ :fg :WINDOW_BORDER :bg :BACKGROUND} (buffer/cell buf 99 29)))))

(describe "scene"
  (it "turns a window and font measurements into draw commands"
    (let [frame (view/scene (themed-state) 960 600 12.0 20.0 5.0)]
      (should= [0 0 0] (:background frame))
      (should (some #(= {:text "V" :x 456 :y 50 :color [255 255 255]} %) (:glyphs frame)))
      (should (some #(= {:x 432 :y 100 :w 12 :h 25 :color [192 192 192]} %) (:rects frame)))))

  (it "centers on a wider grid when the window is wider"
    (let [wide (view/scene (themed-state) 1200 600 12.0 20.0 5.0)
          title-x (fn [frame] (:x (first (filter #(= "V" (:text %)) (:glyphs frame)))))]
      (should= 456 (title-x (view/scene (themed-state) 960 600 12.0 20.0 5.0)))
      (should= 576 (title-x wide)))))
