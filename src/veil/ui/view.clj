(ns veil.ui.view
  "Pure view rendering: translates game state to draw commands. No Quil."
  (:require [veil.game.state :as state]))

(defn- add-position-and-color [width commands]
  "Add :x, :y, and :color to each command based on width and row."
  (mapv (fn [cmd]
          (let [row (:row cmd)
                x (/ width 2)
                y (+ 50 (* row 40))
                color (if (:selected? cmd) [255 255 100] [220 220 220])]
            (assoc cmd :x x :y y :color color)))
        commands))

(defn- frame-main-menu
  "Render the main menu screen."
  [state width]
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
  [state width]
  (vec
    (concat
      [{:text "@" :row 6}
       {:text "Esc: menu" :row 20}])))

(defn- frame-options
  "Render the options screen."
  [state width]
  (vec
    (concat
      [{:text "Options" :row 4}
       {:text "Esc: back" :row 20}])))

(defn frame
  "Generate draw commands for the current state. Each command is a map with
  :text (string), :row (logical line number), optionally :selected? (bool),
  :x (pixel x position), :y (pixel y position), and :color (RGB vector)."
  [state width]
  (let [commands (case (state/screen state)
                   :main-menu (frame-main-menu state width)
                   :map (frame-map state width)
                   :options (frame-options state width)
                   [])]
    (add-position-and-color width commands)))
