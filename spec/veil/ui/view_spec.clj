(ns veil.ui.view-spec
  (:require [speclj.core :refer :all]
            [clojure.string :as string]
            [veil.ui.view :as view]
            [veil.game.state :as state]))

(describe "main menu frame"
  (it "includes a title"
    (let [commands (view/frame (state/initial))]
      (should (some #(= "VEIL" (:text %)) commands))))

  (it "includes all three menu items"
    (let [commands (view/frame (state/initial))]
      (should (some #(= "New Game" (:text %)) commands))
      (should (some #(= "Options" (:text %)) commands))
      (should (some #(= "Quit" (:text %)) commands))))

  (it "marks the first item as selected when New Game is selected"
    (let [s (state/initial)
          commands (view/frame s)
          new-game-cmd (first (filter #(= "New Game" (:text %)) commands))]
      (should= true (:selected? new-game-cmd))))

  (it "marks the second item as selected when Options is selected"
    (let [s (-> (state/initial) (state/handle-input :down))
          commands (view/frame s)
          options-cmd (first (filter #(= "Options" (:text %)) commands))]
      (should= true (:selected? options-cmd))))

  (it "marks only the selected item with :selected? true"
    (let [s (state/initial)
          commands (view/frame s)
          new-game (first (filter #(= "New Game" (:text %)) commands))
          options (first (filter #(= "Options" (:text %)) commands))]
      (should= true (:selected? new-game))
      (should= false (:selected? options))))

  (it "lists the menu items top to bottom with a blank row between each"
    (let [commands (view/frame (state/initial))
          rows (for [label (state/menu-items (state/initial))]
                 (:row (first (filter #(= label (:text %)) commands))))
          gaps (map - (rest rows) rows)]
      (should= [2 2] gaps)))

  (it "includes a hint line"
    (let [commands (view/frame (state/initial))]
      (should (some #(string? (:text %)) commands)))))

(describe "map screen frame"
  (it "shows the player as @"
    (let [s (-> (state/initial) (state/handle-input :confirm))
          commands (view/frame s)]
      (should (some #(= "@" (:text %)) commands))))

  (it "includes an Esc hint"
    (let [s (-> (state/initial) (state/handle-input :confirm))
          commands (view/frame s)]
      (should (some #(string/includes? (:text %) "Esc") commands)))))

(describe "options screen frame"
  (it "shows a heading"
    (let [s (-> (state/initial) (state/handle-input :down) (state/handle-input :confirm))
          commands (view/frame s)]
      (should (some #(= "Options" (:text %)) commands))))

  (it "includes an Esc hint"
    (let [s (-> (state/initial) (state/handle-input :down) (state/handle-input :confirm))
          commands (view/frame s)]
      (should (some #(string/includes? (:text %) "Esc") commands)))))
