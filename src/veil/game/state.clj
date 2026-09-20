(ns veil.game.state
  "Global game state: screens, menu, player, game over flag, mods registry, themes."
  (:require [veil.game.menu :as menu]
            [veil.game.theme :as theme]
            [veil.game.dispatch :as dispatch]))

(defn initial
  "Return the initial game state: main menu with New Game selected, not over."
  []
  {:screen :main-menu
   :menu (menu/new-menu)
   :over? false
   :player {:glyph \@}
   :themes {}
   :active-theme theme/default-id})

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

(defn- main-menu-alias-direction
  "Map a printable character to the menu-move direction it aliases,
  case-insensitively (w = up, s = down). Returns nil for any other character."
  [char]
  (case (when char (Character/toLowerCase char))
    \w :up
    \s :down
    nil))

(defn- handle-main-menu-char
  "Handle a printable-character input on the main menu: the W/S move
  aliases, ignoring any other character."
  [state input]
  (if-let [direction (main-menu-alias-direction (:char input))]
    (update state :menu menu/move direction)
    state))

(defn handle-main-menu
  "Handle input while on the main menu."
  [state input]
  (cond
    (= input :up) (update state :menu menu/move :up)
    (= input :down) (update state :menu menu/move :down)
    (= input :confirm) (select-menu-item state)
    (map? input) (handle-main-menu-char state input)
    :else state))

(defn- back-to-main-menu
  "Return to the main menu on :back input; otherwise leave state unchanged."
  [state input]
  (if (= input :back)
    (assoc state :screen :main-menu)
    state))

(defn handle-map
  "Handle input while on the map screen."
  [state input]
  (back-to-main-menu state input))

(defn handle-options
  "Handle input while on the options screen."
  [state input]
  (back-to-main-menu state input))

(defn handle-input-with-chain
  "Process an input through a dispatch chain and fall through to screen bindings.
  The chain is a sequence of handlers; each is (fn [state input] -> state-or-nil).
  If a handler consumes (returns state), walk stops. If nothing consumes, use
  screen-level bindings."
  [state input handlers]
  (if (nil? input)
    state
    (let [[new-state consumed?] (dispatch/dispatch state input handlers)]
      (if consumed?
        new-state
        (case (screen state)
          :main-menu (handle-main-menu state input)
          :map (handle-map state input)
          :options (handle-options state input)
          state)))))

(defn handle-input
  "Process an input based on the current screen using an empty dispatch chain,
  falling through to screen-level bindings."
  [state input]
  (handle-input-with-chain state input []))

(defn with-mods
  "Associate a mod registry with the game state."
  [state registry]
  (assoc state :mods registry))

(defn mods
  "Get the mod registry from the game state."
  [state]
  (:mods state))

(defn with-themes
  "Associate themes and set the active theme with the game state."
  [state themes]
  (assoc state :themes themes :active-theme theme/default-id))

(defn starting
  "Build the starting game state from a mods registry.
   1-arity: registry only, so no themes are loaded.
   2-arity: registry and the themes map (theme id -> colors) to draw with."
  ([registry]
   (with-mods (initial) registry))
  ([registry themes]
   (with-themes (with-mods (initial) registry) themes)))

(defn stamp-time
  "Stamp a frame's time (milliseconds) into the state."
  [state ms]
  (assoc state :now-ms ms))
