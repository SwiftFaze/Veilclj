(ns veil.ui.view
  "Pure view rendering: translates game state to draw commands. No Quil."
  (:require [veil.game.state :as state]))

(defn- row->y
  "Pixel y of a logical row: a 50 pixel top margin, then 40 pixels per row."
  [row]
  (+ 50 (* row 40)))

(defn- command-color
  "The selected item is highlighted; everything else is plain."
  [{:keys [selected?]}]
  (if selected? [255 255 100] [220 220 220]))

(defn- lay-out
  "Give each command its pixel position and colour. This is here, not in the
  Quil layer, so the drawing code has nothing left to decide or calculate."
  [width commands]
  (mapv (fn [cmd]
          (assoc cmd :x (/ width 2) :y (row->y (:row cmd)) :color (command-color cmd)))
        commands))

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
  :text (string), :row (logical line number), optionally :selected? (bool),
  :x (pixel x position), :y (pixel y position), and :color (RGB vector)."
  [state width]
  (let [commands (case (state/screen state)
                   :main-menu (frame-main-menu state)
                   :map (frame-map state)
                   :options (frame-options state)
                   [])]
    (lay-out width commands)))
