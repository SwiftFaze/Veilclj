(ns veil.acceptance.steps.keyboard-input
  "Acceptance steps for keyboard input and dispatch chain features."
  (:require [veil.acceptance.step-support :refer [ok check fail]]
            [veil.game.state :as state]
            [veil.ui.input :as input]))

(defn- build-event
  "Build a Quil-shaped event map for a key name string."
  [key-name]
  (let [event (case key-name
                "Up" {:key :up :key-code 38 :raw-key (char 65535)}
                "Down" {:key :down :key-code 40 :raw-key (char 65535)}
                "Left" {:key :left :key-code 37 :raw-key (char 65535)}
                "Right" {:key :right :key-code 39 :raw-key (char 65535)}
                "Enter" {:raw-key \newline}
                "Esc" {:raw-key (char 27)}
                "Tab" {:key :tab :raw-key \tab}
                "Space" {:key :space :raw-key \space}
                "Backspace" {:raw-key (char 8)}
                "Delete" {:raw-key (char 127)}
                "Home" {:raw-key (char 36)}
                "End" {:raw-key (char 35)}
                "Page Up" {:raw-key (char 33)}
                "Page Down" {:raw-key (char 34)}
                "F1" {:key :f1 :raw-key (char 65535)}
                "Caps Lock" {:key :caps-lock :raw-key (char 65535)}
                nil)]
    (if (some? event) event {:message (str "unknown key: " key-name)})))

(defn- char-to-event [char-str]
  (if (= 1 (count char-str))
    {:raw-key (first char-str)}
    {:message (str "expected single character, got: " char-str)}))

(def handlers
  [[#"the player types (\S)"
    (fn [world [_ char-str]]
      (let [event (char-to-event char-str)]
        (if (:message event)
          event
          (let [game-input (input/event->input event)
                chain (or (:chain @world) [])
                new-state (state/handle-input-with-chain (:state @world) game-input chain)]
            (swap! world assoc :state new-state :last-input game-input)
            (ok)))))]

   [#"the player types (\S) with Ctrl held"
    (fn [world [_ char-str]]
      (let [event (merge (char-to-event char-str) {:modifiers #{:ctrl}})]
        (if (:message event)
          event
          (let [game-input (input/event->input event)
                chain (or (:chain @world) [])
                new-state (state/handle-input-with-chain (:state @world) game-input chain)]
            (swap! world assoc :state new-state :last-input game-input)
            (ok)))))]

   [#"the player presses Tab with Shift held"
    (fn [world _]
      (let [event {:key :tab :raw-key \tab :modifiers #{:shift}}
            game-input (input/event->input event)
            chain (or (:chain @world) [])
            new-state (state/handle-input-with-chain (:state @world) game-input chain)]
        (swap! world assoc :state new-state :last-input game-input)
        (ok)))]

   [#"the input is the ([\w-]+) action"
    (fn [world [_ action-name]]
      (check (= (keyword action-name) (:last-input @world))
             (str "last input was " (:last-input @world))))]

   [#"the input is the character (\S)"
    (fn [world [_ char-str]]
      (check (= {:char (first char-str)} (:last-input @world))
             (str "last input was " (:last-input @world))))]

   [#"the input is the character (\S) with the ctrl modifier"
    (fn [world [_ char-str]]
      (check (= {:char (first char-str) :mods #{:ctrl}} (:last-input @world))
             (str "last input was " (:last-input @world))))]

   [#"the input is not the (\w+) action"
    (fn [world [_ action-name]]
      (check (not= (keyword action-name) (:last-input @world))
             (str "last input was " (:last-input @world))))]

   [#"there is no input"
    (fn [world _]
      (check (nil? (:last-input @world))
             (str "last input was " (:last-input @world))))]

   [#"a chain of an overlay handler, a pane handler and a widget handler"
    (fn [world _]
      (swap! world assoc :chain [(fn [s i] nil) (fn [s i] nil) (fn [s i] nil)])
      (ok))]

   [#"a chain whose overlay handler consumes every input and opens the map screen"
    (fn [world _]
      (swap! world assoc :chain [(fn [s i] (assoc s :screen :map)) (fn [s i] nil) (fn [s i] nil)])
      (ok))]

   [#"a chain whose pane handler consumes every input and opens the map screen"
    (fn [world _]
      (swap! world assoc :chain [(fn [s i] nil) (fn [s i] (assoc s :screen :map)) (fn [s i] nil)])
      (ok))]

   [#"a chain whose pane handler consumes every input and opens the options screen"
    (fn [world _]
      (swap! world assoc :chain [(fn [s i] nil) (fn [s i] (assoc s :screen :options)) (fn [s i] nil)])
      (ok))]

   [#"a chain whose widget handler consumes every input and opens the options screen"
    (fn [world _]
      (swap! world assoc :chain [(fn [s i] nil) (fn [s i] nil) (fn [s i] (assoc s :screen :options))])
      (ok))]

   [#"a chain whose widget handler consumes every input and opens the map screen"
    (fn [world _]
      (swap! world assoc :chain [(fn [s i] nil) (fn [s i] nil) (fn [s i] (assoc s :screen :map))])
      (ok))]

   [#"a chain whose widget handler consumes every character"
    (fn [world _]
      (swap! world assoc :chain [(fn [s i] nil) (fn [s i] nil) (fn [s i] (if (map? i) s nil))])
      (ok))]

   [#"no handler consumes anything"
    (fn [world _]
      (swap! world assoc :chain [(fn [s i] nil) (fn [s i] nil) (fn [s i] nil)])
      (ok))]

   [#"the overlay handler consumes every input and opens the map screen"
    (fn [world _]
      (let [chain (or (:chain @world) [(fn [s i] nil) (fn [s i] nil) (fn [s i] nil)])]
        (swap! world assoc :chain (assoc chain 0 (fn [s i] (assoc s :screen :map)))))
      (ok))]

   [#"only the overlay handler consumes, opening the map screen"
    (fn [world _]
      (let [chain (or (:chain @world) [(fn [s i] nil) (fn [s i] nil) (fn [s i] nil)])]
        (swap! world assoc :chain (assoc chain 0 (fn [s i] (assoc s :screen :map)))))
      (ok))]

   [#"the pane handler consumes every input and opens the map screen"
    (fn [world _]
      (let [chain (or (:chain @world) [(fn [s i] nil) (fn [s i] nil) (fn [s i] nil)])]
        (swap! world assoc :chain (assoc chain 1 (fn [s i] (assoc s :screen :map)))))
      (ok))]

   [#"the pane handler consumes every input and opens the options screen"
    (fn [world _]
      (let [chain (or (:chain @world) [(fn [s i] nil) (fn [s i] nil) (fn [s i] nil)])]
        (swap! world assoc :chain (assoc chain 1 (fn [s i] (assoc s :screen :options)))))
      (ok))]

   [#"only the pane handler consumes, opening the map screen"
    (fn [world _]
      (let [chain (or (:chain @world) [(fn [s i] nil) (fn [s i] nil) (fn [s i] nil)])]
        (swap! world assoc :chain (assoc chain 1 (fn [s i] (assoc s :screen :map)))))
      (ok))]

   [#"the widget handler consumes every input and opens the map screen"
    (fn [world _]
      (let [chain (or (:chain @world) [(fn [s i] nil) (fn [s i] nil) (fn [s i] nil)])]
        (swap! world assoc :chain (assoc chain 2 (fn [s i] (assoc s :screen :map)))))
      (ok))]

   [#"the widget handler consumes every input and opens the options screen"
    (fn [world _]
      (let [chain (or (:chain @world) [(fn [s i] nil) (fn [s i] nil) (fn [s i] nil)])]
        (swap! world assoc :chain (assoc chain 2 (fn [s i] (assoc s :screen :options)))))
      (ok))]

   [#"only the widget handler consumes, opening the map screen"
    (fn [world _]
      (let [chain (or (:chain @world) [(fn [s i] nil) (fn [s i] nil) (fn [s i] nil)])]
        (swap! world assoc :chain (assoc chain 2 (fn [s i] (assoc s :screen :map)))))
      (ok))]

   [#"an empty chain"
    (fn [world _]
      (swap! world assoc :chain [])
      (ok))]

   [#"the (\w+) action is dispatched"
    (fn [world [_ action-name]]
      (let [action (keyword action-name)
            chain (or (:chain @world) [])
            new-state (state/handle-input-with-chain (:state @world) action chain)]
        (swap! world assoc :state new-state)
        (ok)))]

   [#"the frame is stamped at (\d+) ms"
    (fn [world [_ ms-str]]
      (swap! world assoc :state-before (:state @world))
      (swap! world assoc :state (state/stamp-time (:state @world) (Long/parseLong ms-str)))
      (ok))]

   [#"the frame is stamped at (\d+) ms twice over"
    (fn [world [_ ms-str]]
      (let [ms (Long/parseLong ms-str)
            s1 (state/stamp-time (:state @world) ms)
            s2 (state/stamp-time s1 ms)]
        (swap! world assoc :state s1 :state-2 s2))
      (ok))]

   [#"the state's time is (\d+) ms"
    (fn [world [_ ms-str]]
      (check (= (Long/parseLong ms-str) (:now-ms (:state @world)))
             (str "state's time is " (:now-ms (:state @world)))))]

   [#"the state has no time"
    (fn [world _]
      (check (nil? (:now-ms (:state @world)))
             (str "state's time is " (:now-ms (:state @world)))))]

   [#"both results are the same state"
    (fn [world _]
      (check (= (:state @world) (:state-2 @world))
             "results differ"))]

   [#"the state differs from before only in its time"
    (fn [world _]
      (let [before (:state-before @world)
            after (:state @world)
            before-no-time (dissoc before :now-ms)
            after-no-time (dissoc after :now-ms)]
        (check (= before-no-time after-no-time)
               "state differs in more than time")))]])
