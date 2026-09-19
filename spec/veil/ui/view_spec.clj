(ns veil.ui.view-spec
  (:require [speclj.core :refer :all]
            [clojure.string :as string]
            [veil.ui.view :as view]
            [veil.game.state :as state]))

(describe "main menu frame"
  (it "includes a title"
    (let [commands (view/frame (state/initial) 960)]
      (should (some #(= "VEIL" (:text %)) commands))))

  (it "includes all three menu items"
    (let [commands (view/frame (state/initial) 960)]
      (should (some #(= "New Game" (:text %)) commands))
      (should (some #(= "Options" (:text %)) commands))
      (should (some #(= "Quit" (:text %)) commands))))

  (it "marks the first item as selected when New Game is selected"
    (let [s (state/initial)
          commands (view/frame s 960)
          new-game-cmd (first (filter #(= "New Game" (:text %)) commands))]
      (should= true (:selected? new-game-cmd))))

  (it "marks the second item as selected when Options is selected"
    (let [s (-> (state/initial) (state/handle-input :down))
          commands (view/frame s 960)
          options-cmd (first (filter #(= "Options" (:text %)) commands))]
      (should= true (:selected? options-cmd))))

  (it "marks only the selected item with :selected? true"
    (let [s (state/initial)
          commands (view/frame s 960)
          new-game (first (filter #(= "New Game" (:text %)) commands))
          options (first (filter #(= "Options" (:text %)) commands))]
      (should= true (:selected? new-game))
      (should= false (:selected? options))))

  (it "lists the menu items top to bottom with a blank row between each"
    (let [commands (view/frame (state/initial) 960)
          rows (for [label (state/menu-items (state/initial))]
                 (:row (first (filter #(= label (:text %)) commands))))
          gaps (map - (rest rows) rows)]
      (should= [2 2] gaps)))

  (it "includes a hint line"
    (let [commands (view/frame (state/initial) 960)]
      (should (some #(string? (:text %)) commands))))

  (it "selected menu item has highlight colour"
    (let [s (state/initial)
          commands (view/frame s 960)
          new-game-cmd (first (filter #(= "New Game" (:text %)) commands))]
      (should= [255 255 100] (:color new-game-cmd))))

  (it "unselected menu item has normal colour"
    (let [s (state/initial)
          commands (view/frame s 960)
          options-cmd (first (filter #(= "Options" (:text %)) commands))]
      (should= [220 220 220] (:color options-cmd))))

  (it "title has normal colour"
    (let [commands (view/frame (state/initial) 960)
          title-cmd (first (filter #(= "VEIL" (:text %)) commands))]
      (should= [220 220 220] (:color title-cmd))))

  (it "menu item at row 2 has x=480 for 960px wide window"
    (let [commands (view/frame (state/initial) 960)
          title-cmd (first (filter #(= "VEIL" (:text %)) commands))]
      (should= 480 (:x title-cmd))))

  (it "menu item at row 2 has y=130 for 960px wide window"
    (let [commands (view/frame (state/initial) 960)
          title-cmd (first (filter #(= "VEIL" (:text %)) commands))]
      (should= 130 (:y title-cmd))))

  (it "menu item at row 4 has y=210 for 960px wide window"
    (let [commands (view/frame (state/initial) 960)
          new-game-cmd (first (filter #(= "New Game" (:text %)) commands))]
      (should= 210 (:y new-game-cmd))))

  (it "menu item at row 12 has y=530 for 960px wide window"
    (let [commands (view/frame (state/initial) 960)
          hint-cmd (first (filter #(string/includes? (:text %) "Use Up") commands))]
      (should= 530 (:y hint-cmd))))

  (it "menu item at row 2 has x=400 for 800px wide window"
    (let [commands (view/frame (state/initial) 800)
          title-cmd (first (filter #(= "VEIL" (:text %)) commands))]
      (should= 400 (:x title-cmd)))))

(describe "map screen frame"
  (it "shows the player as @"
    (let [s (-> (state/initial) (state/handle-input :confirm))
          commands (view/frame s 960)]
      (should (some #(= "@" (:text %)) commands))))

  (it "includes an Esc hint"
    (let [s (-> (state/initial) (state/handle-input :confirm))
          commands (view/frame s 960)]
      (should (some #(string/includes? (:text %) "Esc") commands)))))

(describe "options screen frame"
  (it "shows a heading"
    (let [s (-> (state/initial) (state/handle-input :down) (state/handle-input :confirm))
          commands (view/frame s 960)]
      (should (some #(= "Options" (:text %)) commands))))

  (it "includes an Esc hint"
    (let [s (-> (state/initial) (state/handle-input :down) (state/handle-input :confirm))
          commands (view/frame s 960)]
      (should (some #(string/includes? (:text %) "Esc") commands)))))
