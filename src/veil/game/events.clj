(ns veil.game.events
  "Derive events by diffing game states."
  (:require [veil.game.state :as state]))

(defn- selection-event [before after]
  (let [before-item (state/selected-item before)
        after-item (state/selected-item after)]
    (when (not= before-item after-item)
      {:event :menu/selection-changed :to (keyword (clojure.string/replace (clojure.string/lower-case after-item) " " "-"))})))

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
  (cond-> []
    (selection-event before after) (conj (selection-event before after))
    (screen-event before after) (conj (screen-event before after))
    (over-event before after) (conj (over-event before after))))
