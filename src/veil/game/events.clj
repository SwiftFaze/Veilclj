(ns veil.game.events
  "Derive events by diffing game states."
  (:require [clojure.string :as str]
            [veil.game.state :as state]))

(defn- item->keyword
  "\"New Game\" -> :new-game"
  [item]
  (keyword (str/replace (str/lower-case item) " " "-")))

(defn- selection-event [before after]
  (let [after-item (state/selected-item after)]
    (when (not= (state/selected-item before) after-item)
      {:event :menu/selection-changed :to (item->keyword after-item)})))

(defn- screen-event [before after]
  (let [before-screen (state/screen before)
        after-screen (state/screen after)]
    (when (not= before-screen after-screen)
      {:event :screen/changed :from before-screen :to after-screen})))

(defn- over-event [before after]
  (when (and (not (state/over? before)) (state/over? after))
    {:event :game/over}))

(defn between
  "Return a vector of events derived by diffing two game states, in order:
   selection, screen, over."
  [before after]
  (vec (remove nil? [(selection-event before after)
                     (screen-event before after)
                     (over-event before after)])))
