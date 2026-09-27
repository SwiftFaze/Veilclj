(ns veil.ui.view
  "Pure view rendering: translates game state to a buffer, then to draw commands.
   No Quil, no decisions - pure data pipeline."
  (:require [veil.game.state :as state]
            [veil.ui.buffer :as buffer]
            [veil.ui.grid :as grid]
            [veil.ui.commands :as commands]
            [veil.ui.widgets :as widgets]))

(defn- main-menu-lines
  "The main menu's lines; only the selected menu item is :selected?."
  [game-state]
  (let [selected (state/selected-item game-state)]
    (concat [{:text "VEIL" :row 2}]
            (map-indexed (fn [i item]
                           {:text item :row (+ 4 (* i 2)) :selected? (= item selected)})
                         (state/menu-items game-state))
            [{:text "Use Up/Down or W/S to move, Enter to select" :row 12}])))

(defn- widget-gallery-buffer
  "Render the widget gallery screen into a buffer."
  [state cols rows]
  (let [;; Start with a blank buffer
        buf (buffer/blank cols rows)

        ;; Row 0: title bar
        with-title-bar (widgets/title-bar buf "VEIL" "Widget Gallery" 0)

        ;; Rows 1-6: "Items" frame
        with-items-frame (widgets/frame with-title-bar 0 1 cols 6 "Items" :WINDOW_BORDER :BACKGROUND)

        ;; Rows 2-5: item list with accelerators (interior of Items frame)
        items [["Apple" \a]
               ["Banana" \b]
               ["Grape" \g]
               ["Guava" \u]]
        with-items
        (reduce (fn [b [idx [item letter]]]
                  (widgets/accelerator-label b item letter 3 (+ 2 idx) :NORMAL_TEXT :BACKGROUND))
                with-items-frame
                (map-indexed (fn [i item] [i item]) items))

        ;; Rows 8-10: "Badges" frame
        with-badges-frame (widgets/frame with-items 0 8 cols 3 "Badges" :WINDOW_BORDER :BACKGROUND)

        ;; Row 9: badges (SUC/ERR/WRN/INF/ACC at columns 3, 9, 15, 21, 27)
        badges [[:SUCCESS "SUC" 3]
                [:ERROR "ERR" 9]
                [:WARNING "WRN" 15]
                [:INFO "INF" 21]
                [:ACCENT "ACC" 27]]
        with-badges
        (reduce (fn [b [color abbr col]]
                  (widgets/badge b color abbr col 9))
                with-badges-frame
                badges)

        ;; Rows 12-14: "Chips" frame
        with-chips-frame (widgets/frame with-badges 0 12 cols 3 "Chips" :WINDOW_BORDER :BACKGROUND)

        ;; Row 13: chips (same colors and columns)
        chips [[:SUCCESS "SUC" 3 false]
               [:ERROR "ERR" 9 false]
               [:WARNING "WRN" 15 false]
               [:INFO "INF" 21 false]
               [:ACCENT "ACC" 27 false]]
        with-chips
        (reduce (fn [b [color abbr col focused?]]
                  (widgets/chip b color abbr col 13 focused?))
                with-chips-frame
                chips)

        ;; Row 22: status line
        status-message (get-in state [:gallery :status])
        with-status-line (widgets/status-line with-chips status-message 22)

        ;; Row 23 and up: hint bar (docks at bottom, grows upward if needed)
        hints (state/gallery-hints)
        with-hints (widgets/hint-bar with-status-line hints)]

    with-hints))

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

(defn- write-line
  "buf with one screen line written centered across cols. The selected line is
   drawn in reverse video, every other line in normal text."
  [cols buf {:keys [text row selected?]}]
  (let [col (quot (- cols (count text)) 2)
        [fg bg] (if selected?
                  [:SELECTED_TEXT :SELECTED_HIGHLIGHT]
                  [:NORMAL_TEXT :BACKGROUND])]
    (buffer/write-text buf col row text fg bg)))

(defn buffer
  "Render the game state into a cell buffer.
   For most screens: writes each line centered, the selected menu item in reverse video,
   and draws a single-line border around the whole grid.
   For the widget gallery: renders the gallery with its own chrome (title bar, frame, status line, hint bar)
   instead of the whole-grid border."
  [state cols rows]
  (if (= (state/screen state) :widget-gallery)
    (widget-gallery-buffer state cols rows)
    (let [lines-drawn (reduce (partial write-line cols)
                              (buffer/blank cols rows)
                              (lines-for-screen state))]
      (buffer/draw-box lines-drawn 0 0 cols rows :WINDOW_BORDER :BACKGROUND))))

(defn scene
  "Compose the full rendering pipeline: state -> buffer -> commands.
   Returns the draw commands ready for Quil."
  [state win-w win-h text-w ascent descent]
  (let [cell-size (grid/cell-size text-w ascent descent)
        grid-size (grid/size win-w win-h (:w cell-size) (:h cell-size))
        buf (buffer state (:cols grid-size) (:rows grid-size))]
    (commands/frame state buf (:w cell-size) (:h cell-size))))
