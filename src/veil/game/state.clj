(ns veil.game.state
  "Global game state: screens, menu, player, game over flag."
  (:require [veil.game.menu :as menu]))

(defn initial
  "Return the initial game state: main menu with New Game selected, not over."
  []
  {:screen :main-menu
   :menu (menu/new-menu)
   :over? false
   :player {:glyph \@}})

(defn screen
  "Get the current screen keyword (:main-menu, :map, :options)."
  [state]
  (:screen state))

(defn menu-items
  "Get the current menu items vector."
  [state]
  (:items (:menu state)))

(defn selected-item
  "Get the label of the currently selected menu item."
  [state]
  (menu/selected-item (:menu state)))

(defn player-glyph
  "Get the player character glyph."
  [state]
  (get-in state [:player :glyph]))

(defn over?
  "Check if the game is over."
  [state]
  (:over? state))

(defn select-menu-item
  "Perform the action for the selected menu item."
  [state]
  (case (selected-item state)
    "New Game" (assoc state :screen :map)
    "Options" (assoc state :screen :options)
    "Quit" (assoc state :over? true)
    state))

(defn handle-main-menu
  "Handle input while on the main menu."
  [state input]
  (case input
    :up (update state :menu menu/move :up)
    :down (update state :menu menu/move :down)
    :confirm (select-menu-item state)
    state))

(defn handle-map
  "Handle input while on the map screen."
  [state input]
  (if (= input :back)
    (assoc state :screen :main-menu)
    state))

(defn handle-options
  "Handle input while on the options screen."
  [state input]
  (if (= input :back)
    (assoc state :screen :main-menu)
    state))

(defn handle-input
  "Process an input (:up, :down, :confirm, :back, or nil/unknown) based on the current screen."
  [state input]
  (if (nil? input)
    state
    (case (screen state)
      :main-menu (handle-main-menu state input)
      :map (handle-map state input)
      :options (handle-options state input)
      state)))
