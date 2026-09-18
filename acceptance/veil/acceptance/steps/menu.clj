(ns veil.acceptance.steps.menu
  "Acceptance steps for the main menu feature."
  (:require [veil.acceptance.step-support :refer [ok check fail]]
            [veil.game.state :as state]
            [veil.ui.input :as input]))

(defn- build-event
  "Build a Quil-shaped event map for a key name string."
  [key-name]
  (case key-name
    "Down" {:key :down :key-code 40 :raw-key (char 65535)}
    "Up" {:key :up :key-code 38 :raw-key (char 65535)}
    "Left" {:key :left :key-code 37 :raw-key (char 65535)}
    "Right" {:key :right :key-code 39 :raw-key (char 65535)}
    "S" {:key :s :raw-key \S}
    "W" {:key :w :raw-key \W}
    "X" {:key :x :raw-key \x}
    "Space" {:key :space :raw-key \space}
    "Enter" {:raw-key \newline}
    "Esc" {:raw-key (char 27)}
    (fail (str "unknown key: " key-name))))

(def handlers
  [[#"the game has just started"
    (fn [world _]
      (swap! world assoc :state (state/initial))
      (ok))]

   [#"the current screen is (.+)"
    (fn [world [_ screen-name]]
      (let [expected (case screen-name
                       "the main menu" :main-menu
                       "the map screen" :map
                       "the options screen" :options
                       (throw (Exception. (str "unknown screen: " screen-name))))
            actual (state/screen (:state @world))]
        (check (= expected actual)
               (str "screen is " actual))))]

   [#"the menu items are ([^,]+(?:, [^,]+)*)"
    (fn [world [_ items-str]]
      (let [expected (mapv clojure.string/trim (clojure.string/split items-str #","))
            actual (state/menu-items (:state @world))]
        (check (= expected actual)
               (str "menu items are " actual))))]

   [#"the selected menu item is (.+)"
    (fn [world [_ item-name]]
      (let [s (:state @world)
            items (state/menu-items s)
            target-idx (first (keep-indexed (fn [i itm] (when (= itm item-name) i)) items))]
        (if (some? target-idx)
          (let [num-items (count items)
                new-state (loop [st s, presses 0]
                            (if (or (= (:selected (:menu st)) target-idx)
                                    (>= presses num-items))
                              st
                              (recur (state/handle-input st :down) (inc presses))))]
            (swap! world assoc :state new-state)
            (check (= item-name (state/selected-item new-state))
                   (str "selected item is " (state/selected-item new-state))))
          (fail (str "item not found: " item-name)))))]

   [#"the player presses (\w+)"
    (fn [world [_ key-name]]
      (let [event (build-event key-name)]
        (if (:message event)
          event
          (let [game-input (input/event->input event)
                new-state (state/handle-input (:state @world) game-input)]
            (swap! world assoc :state new-state)
            (ok)))))]

   [#"the map shows the player as (.)"
    (fn [world [_ glyph-str]]
      (let [glyph (first glyph-str)
            actual (state/player-glyph (:state @world))]
        (check (= glyph actual)
               (str "player glyph is " actual))))]

   [#"the game is over"
    (fn [world _]
      (check (state/over? (:state @world))
             "game is not over"))]

   [#"the game is not over"
    (fn [world _]
      (check (not (state/over? (:state @world)))
             "game is over"))]])
