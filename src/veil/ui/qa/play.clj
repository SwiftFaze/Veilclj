(ns veil.ui.qa.play
  "Play a script through the game state."
  (:require [veil.ui.qa.script :as script]
            [veil.game.events :as events]))

(defn- send-event
  "Send event through handle, returning {:state new-state :entries [...]}: the
   key entry (logged as key-kw), then the events derived from the state change,
   all stamped with tick."
  [handle state tick event key-kw]
  (let [new-state (handle state event)
        stamp #(assoc % :tick tick)]
    {:state new-state
     :entries (into [{:tick tick :key key-kw}]
                    (map stamp (events/between state new-state)))}))

(defn press
  "Press a key once, returning {:state new-state :entries [...]}.
   Each entry has :tick and :key, plus any events from events/between."
  [handle state tick key-name]
  (send-event handle state tick
              (script/key->event key-name)
              (script/key-keyword key-name)))

(defn handle-event
  "Like press, for a live Quil-shaped event rather than a scripted key name."
  [handle state tick event]
  (send-event handle state tick event (script/event->key-keyword event)))

(defn play
  "Play a sequence of steps through the game, returning
   {:state final-state :entries all-entries :finished? true}."
  [handle initial-state steps]
  (let [result (reduce
                 (fn [acc step]
                   (let [pressed (press handle (:state acc) (:tick step) (:key step))]
                     {:state (:state pressed)
                      :entries (into (:entries acc) (:entries pressed))}))
                 {:state initial-state :entries []}
                 steps)]
    (assoc result :finished? true)))
