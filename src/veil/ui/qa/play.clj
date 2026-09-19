(ns veil.ui.qa.play
  "Play a script through the game state."
  (:require [veil.ui.qa.script :as script]
            [veil.game.events :as events]))

(defn press
  "Press a key once, returning {:state new-state :entries [...]}.
   Each entry has :tick and :key, plus any events from events/between."
  [handle state tick key-name]
  (let [event (script/key->event key-name)
        new-state (handle state event)
        events-list (events/between state new-state)
        key-kw (script/key-keyword key-name)
        key-entry {:tick tick :key key-kw}
        event-entries (map #(assoc % :tick tick) events-list)
        entries (conj event-entries key-entry)]
    {:state new-state
     :entries (vec (concat [key-entry] event-entries))}))

(defn play
  "Play a sequence of steps through the game, returning
   {:state final-state :entries all-entries :finished? true}."
  [handle initial-state steps]
  (let [result (reduce
                 (fn [acc step]
                   (let [press-result (press handle (:state acc) (:tick step) (:key step))]
                     {:state (:state press-result)
                      :entries (vec (concat (:entries acc) (:entries press-result)))}))
                 {:state initial-state :entries []}
                 steps)]
    (assoc result :finished? true)))
