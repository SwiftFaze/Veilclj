(ns veil.ui.view
  "Pure view rendering: translates game state to draw commands. No Quil."
  (:require [veil.game.state :as state]))

(defn- frame-main-menu
  "Render the main menu screen."
  [state]
  (let [items (state/menu-items state)
        selected-label (state/selected-item state)]
    (vec
      (concat
        [{:text "VEIL" :row 2}]
        (map-indexed (fn [idx label]
                       {:text label
                        :row (+ 4 (* idx 2))
                        :selected? (= label selected-label)})
                     items)
        [{:text "Use Up/Down or W/S to move, Enter to select" :row 12}]))))

(defn- frame-map
  "Render the map screen."
  [state]
  (vec
    (concat
      [{:text "@" :row 6}
       {:text "Esc: menu" :row 20}])))

(defn- frame-options
  "Render the options screen."
  [state]
  (vec
    (concat
      [{:text "Options" :row 4}
       {:text "Esc: back" :row 20}])))

(defn frame
  "Generate draw commands for the current state. Each command is a map with
  :text (string), :row (logical line number), and optionally :selected? (bool)."
  [state]
  (case (state/screen state)
    :main-menu (frame-main-menu state)
    :map (frame-map state)
    :options (frame-options state)
    []))
