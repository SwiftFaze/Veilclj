(ns veil.ui.view
  "Pure view rendering: translates game state to a buffer, then to draw commands.
   No Quil, no decisions - pure data pipeline."
  (:require [veil.game.state :as state]
            [veil.ui.buffer :as buffer]
            [veil.ui.grid :as grid]
            [veil.ui.commands :as commands]))

(defn- lines-for-screen
  "Get the text lines for the current screen: [text row]."
  [game-state]
  (case (state/screen game-state)
    :main-menu (let [items (state/menu-items game-state)
                     selected (state/selected-item game-state)]
                 [["VEIL" 2]
                  (map-indexed (fn [i item]
                                 [item (+ 4 (* i 2))])
                               items)
                  ["Use Up/Down or W/S to move, Enter to select" 12]])
    :map [["@" 6]
          ["Esc: menu" 20]]
    :options [["Options" 4]
              ["Esc: back" 20]]
    []))

(defn- flatten-lines [screen-lines]
  "Flatten nested line sequences into a single sequence."
  (for [group screen-lines
        line (if (sequential? (first group)) group [group])]
    line))

(defn buffer
  "Render the game state into a cell buffer.
   Writes each screen line centered horizontally, preserving its row position."
  [state cols rows]
  (let [lines (flatten-lines (lines-for-screen state))
        selected (state/selected-item state)]
    (reduce (fn [buf [text row]]
              (let [col (quot (- cols (count text)) 2)
                    is-selected? (= text selected)
                    fg (if is-selected? :SELECTED_TEXT :NORMAL_TEXT)
                    bg (if is-selected? :SELECTED_HIGHLIGHT :BACKGROUND)]
                (buffer/write-text buf col row text fg bg)))
            (buffer/blank cols rows)
            lines)))

(defn scene
  "Compose the full rendering pipeline: state -> buffer -> commands.
   Returns the draw commands ready for Quil."
  [state win-w win-h text-w ascent descent]
  (let [cell-size (grid/cell-size text-w ascent descent)
        grid-size (grid/size win-w win-h (:w cell-size) (:h cell-size))
        buf (buffer state (:cols grid-size) (:rows grid-size))]
    (commands/frame state buf (:w cell-size) (:h cell-size))))
