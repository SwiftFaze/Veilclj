(ns veil.acceptance.steps
  "Step dispatch. Handlers live in one namespace per concept under
  veil.acceptance.steps.*; add a new concept's handlers to `step-handlers`."
  (:require [veil.acceptance.step-support :refer [fail]]
            [veil.acceptance.steps.window :as window]
            [veil.acceptance.steps.menu :as menu]
            [veil.acceptance.steps.mods :as mods]
            [veil.acceptance.steps.qa :as qa]))

(def step-handlers
  (concat window/handlers menu/handlers mods/handlers qa/handlers))

(defn handle-step [world text]
  (if-let [[handler match] (some (fn [[pattern handler]]
                                   (when-let [match (re-matches pattern text)]
                                     [handler match]))
                                 step-handlers)]
    (handler world match)
    (fail (str "unsupported acceptance step: " text))))
