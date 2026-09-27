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

        ;; Rows 1-8: "Items" frame (width 12, height 8, left column)
        ;; Interior: row 2 (pad), rows 3-6 (items), row 7 (pad)
        with-items-frame (widgets/frame with-title-bar 0 1 12 8 "Items" :WINDOW_BORDER :BACKGROUND)

        ;; Rows 3-6: item list with accelerators (interior of Items frame)
        items [["Apple" \a]
               ["Banana" \b]
               ["Grape" \g]
               ["Guava" \u]]
        with-items
        (reduce (fn [b [idx [item letter]]]
                  (widgets/accelerator-label b item letter 3 (+ 3 idx) :NORMAL_TEXT :BACKGROUND))
                with-items-frame
                (map-indexed (fn [i item] [i item]) items))

        ;; Rows 1-5: "Badges" frame (width 35, height 5, right column)
        ;; Interior: row 2 (pad), row 3 (content), row 4 (pad)
        with-badges-frame (widgets/frame with-items 13 1 35 5 "Badges" :WINDOW_BORDER :BACKGROUND)

        ;; Row 3: badges (SUC/ERR/WRN/INF/ACC at columns 16, 22, 28, 34, 40)
        badges [[:SUCCESS "SUC" 16]
                [:ERROR "ERR" 22]
                [:WARNING "WRN" 28]
                [:INFO "INF" 34]
                [:ACCENT "ACC" 40]]
        with-badges
        (reduce (fn [b [color abbr col]]
                  (widgets/badge b color abbr col 3))
                with-badges-frame
                badges)

        ;; Rows 6-10: "Chips" frame (width 35, height 5, right column, below Badges)
        ;; Interior: row 7 (pad), row 8 (content), row 9 (pad)
        with-chips-frame (widgets/frame with-badges 13 6 35 5 "Chips" :WINDOW_BORDER :BACKGROUND)

        ;; Row 8: chips (same colors and columns as badges)
        chips [[:SUCCESS "SUC" 16 false]
               [:ERROR "ERR" 22 false]
               [:WARNING "WRN" 28 false]
               [:INFO "INF" 34 false]
               [:ACCENT "ACC" 40 false]]
        with-chips
        (reduce (fn [b [color abbr col focused?]]
                  (widgets/chip b color abbr col 8 focused?))
                with-chips-frame
                chips)

        ;; Rows 11-15: "Message" frame (width 26, height 5, right column, below Chips)
        ;; Interior: row 12 (pad), row 13 (content), row 14 (pad)
        with-message-frame (widgets/frame with-chips 13 11 26 5 "Message" :WINDOW_BORDER :BACKGROUND)

        ;; Row 13: status message text (custom, not using status-line widget)
        status-message (get-in state [:gallery :status])
        with-message
        (if status-message
          (buffer/write-text with-message-frame 15 13 (str " [ " status-message " ] ") :SELECTED_TEXT :SELECTED_HIGHLIGHT)
          with-message-frame)

        ;; Row 23: hint bar (docks at bottom, grows upward if needed)
        hints (state/gallery-hints)
        with-hints (widgets/hint-bar with-message hints)]

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
