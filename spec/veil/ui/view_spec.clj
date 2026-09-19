(ns veil.ui.view-spec
  (:require [speclj.core :refer :all]
            [clojure.string :as string]
            [veil.ui.view :as view]
            [veil.game.state :as state]
            [veil.game.theme :as theme]))

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

(describe "main menu frame"
  (it "includes a title"
    (let [commands (view/frame (themed-state) 960)]
      (should (some #(= "VEIL" (:text %)) commands))))

  (it "includes all three menu items"
    (let [commands (view/frame (themed-state) 960)]
      (should (some #(= "New Game" (:text %)) commands))
      (should (some #(= "Options" (:text %)) commands))
      (should (some #(= "Quit" (:text %)) commands))))

  (it "marks the first item as selected when New Game is selected"
    (let [s (themed-state)
          commands (view/frame s 960)
          new-game-cmd (first (filter #(= "New Game" (:text %)) commands))]
      (should= true (:selected? new-game-cmd))))

  (it "marks the second item as selected when Options is selected"
    (let [s (-> (themed-state) (state/handle-input :down))
          commands (view/frame s 960)
          options-cmd (first (filter #(= "Options" (:text %)) commands))]
      (should= true (:selected? options-cmd))))

  (it "marks only the selected item with :selected? true"
    (let [s (themed-state)
          commands (view/frame s 960)
          new-game (first (filter #(= "New Game" (:text %)) commands))
          options (first (filter #(= "Options" (:text %)) commands))]
      (should= true (:selected? new-game))
      (should= false (:selected? options))))

  (it "lists the menu items top to bottom with a blank row between each"
    (let [commands (view/frame (themed-state) 960)
          rows (for [label (state/menu-items (themed-state))]
                 (:row (first (filter #(= label (:text %)) commands))))
          gaps (map - (rest rows) rows)]
      (should= [2 2] gaps)))

  (it "includes a hint line"
    (let [commands (view/frame (themed-state) 960)]
      (should (some #(string? (:text %)) commands))))

  (it "selected menu item has highlight colour"
    (let [s (themed-state)
          commands (view/frame s 960)
          new-game-cmd (first (filter #(= "New Game" (:text %)) commands))]
      (should= [192 192 192] (:color new-game-cmd))))

  (it "unselected menu item has normal colour"
    (let [s (themed-state)
          commands (view/frame s 960)
          options-cmd (first (filter #(= "Options" (:text %)) commands))]
      (should= [255 255 255] (:color options-cmd))))

  (it "title has normal colour"
    (let [commands (view/frame (themed-state) 960)
          title-cmd (first (filter #(= "VEIL" (:text %)) commands))]
      (should= [255 255 255] (:color title-cmd))))

  (it "menu item at row 2 has x=480 for 960px wide window"
    (let [commands (view/frame (themed-state) 960)
          title-cmd (first (filter #(= "VEIL" (:text %)) commands))]
      (should= 480 (:x title-cmd))))

  (it "menu item at row 2 has y=130 for 960px wide window"
    (let [commands (view/frame (themed-state) 960)
          title-cmd (first (filter #(= "VEIL" (:text %)) commands))]
      (should= 130 (:y title-cmd))))

  (it "menu item at row 4 has y=210 for 960px wide window"
    (let [commands (view/frame (themed-state) 960)
          new-game-cmd (first (filter #(= "New Game" (:text %)) commands))]
      (should= 210 (:y new-game-cmd))))

  (it "menu item at row 12 has y=530 for 960px wide window"
    (let [commands (view/frame (themed-state) 960)
          hint-cmd (first (filter #(string/includes? (:text %) "Use Up") commands))]
      (should= 530 (:y hint-cmd))))

  (it "menu item at row 2 has x=400 for 800px wide window"
    (let [commands (view/frame (themed-state) 800)
          title-cmd (first (filter #(= "VEIL" (:text %)) commands))]
      (should= 400 (:x title-cmd)))))

(describe "map screen frame"
  (it "shows the player as @"
    (let [s (-> (themed-state) (state/handle-input :confirm))
          commands (view/frame s 960)]
      (should (some #(= "@" (:text %)) commands))))

  (it "includes an Esc hint"
    (let [s (-> (themed-state) (state/handle-input :confirm))
          commands (view/frame s 960)]
      (should (some #(string/includes? (:text %) "Esc") commands)))))

(describe "options screen frame"
  (it "shows a heading"
    (let [s (-> (themed-state) (state/handle-input :down) (state/handle-input :confirm))
          commands (view/frame s 960)]
      (should (some #(= "Options" (:text %)) commands))))

  (it "includes an Esc hint"
    (let [s (-> (themed-state) (state/handle-input :down) (state/handle-input :confirm))
          commands (view/frame s 960)]
      (should (some #(string/includes? (:text %) "Esc") commands)))))

(describe "background"
  (it "is the active theme's BACKGROUND color"
    (let [s (assoc-in (themed-state) [:themes "core:default" :BACKGROUND] [5 5 5])]
      (should= [5 5 5] (view/background s))))

  (it "follows the active theme when another theme is made active"
    (let [s (-> (themed-state)
                (assoc-in [:themes "other:dark"] {:BACKGROUND [9 9 9]})
                (theme/activate "other:dark"))]
      (should= [9 9 9] (view/background s)))))
