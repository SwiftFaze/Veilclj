(ns veil.game.menu-spec
  (:require [speclj.core :refer :all]
            [veil.game.menu :as menu]))

(describe "new-menu"
  (it "creates a menu with New Game, Options, Quit items"
    (let [m (menu/new-menu)]
      (should= ["New Game" "Options" "Quit"] (:items m))))

  (it "starts with New Game selected (index 0)"
    (let [m (menu/new-menu)]
      (should= 0 (:selected m)))))

(describe "move"
  (it "moves down to the next item"
    (let [m (menu/new-menu)]
      (should= 1 (:selected (menu/move m :down)))))

  (it "moves up to the previous item"
    (let [m (-> (menu/new-menu) (menu/move :down))]
      (should= 0 (:selected (menu/move m :up)))))

  (it "wraps around from last item to first when moving down"
    (let [m (-> (menu/new-menu) (menu/move :down) (menu/move :down) (menu/move :down))]
      (should= 0 (:selected m))))

  (it "wraps around from first item to last when moving up"
    (let [m (menu/new-menu)]
      (should= 2 (:selected (menu/move m :up)))))

  (it "ignores unknown directions"
    (let [m (menu/new-menu)]
      (should= m (menu/move m :unknown)))))

(describe "selected-item"
  (it "returns the label of the selected item"
    (let [m (menu/new-menu)]
      (should= "New Game" (menu/selected-item m))))

  (it "returns the correct item when selection changes"
    (let [m (-> (menu/new-menu) (menu/move :down))]
      (should= "Options" (menu/selected-item m)))))
