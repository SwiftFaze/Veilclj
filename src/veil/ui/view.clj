(ns veil.ui.view
  "Pure view rendering: translates game state to a buffer, then to draw commands.
   No Quil, no decisions - pure data pipeline."
  (:require [veil.game.state :as state]
            [veil.ui.buffer :as buffer]
            [veil.ui.grid :as grid]
            [veil.ui.commands :as commands]))

(defn- main-menu-lines
  "The main menu's lines; only the selected menu item is :selected?."
  [game-state]
  (let [selected (state/selected-item game-state)]
    (concat [{:text "VEIL" :row 2}]
            (map-indexed (fn [i item]
                           {:text item :row (+ 4 (* i 2)) :selected? (= item selected)})
                         (state/menu-items game-state))
            [{:text "Use Up/Down or W/S to move, Enter to select" :row 12}])))

(defn- lines-for-screen
  "The lines of the current screen: maps of :text, :row and, on the main menu, :selected?."
  [game-state]
  (case (state/screen game-state)
    :main-menu (main-menu-lines game-state)
    :map [{:text "@" :row 6}
          {:text "Esc: menu" :row 20}]
    :options [{:text "Options" :row 4}
              {:text "Esc: back" :row 20}]
    []))

(defn buffer
  "Render the game state into a cell buffer.
   Writes each screen line centered horizontally, preserving its row position.
   The selected menu item is drawn in reverse video; every other line in normal text.
   Draws a single-line border around the whole grid's edges."
  [state cols rows]
  (let [buf (reduce (fn [b {:keys [text row selected?]}]
                      (let [col (quot (- cols (count text)) 2)
                            fg (if selected? :SELECTED_TEXT :NORMAL_TEXT)
                            bg (if selected? :SELECTED_HIGHLIGHT :BACKGROUND)]
                        (buffer/write-text b col row text fg bg)))
                    (buffer/blank cols rows)
                    (lines-for-screen state))]
    (buffer/draw-box buf 0 0 cols rows :WINDOW_BORDER :BACKGROUND)))

(defn scene
  "Compose the full rendering pipeline: state -> buffer -> commands.
   Returns the draw commands ready for Quil."
  [state win-w win-h text-w ascent descent]
  (let [cell-size (grid/cell-size text-w ascent descent)
        grid-size (grid/size win-w win-h (:w cell-size) (:h cell-size))
        buf (buffer state (:cols grid-size) (:rows grid-size))]
    (commands/frame state buf (:w cell-size) (:h cell-size))))
