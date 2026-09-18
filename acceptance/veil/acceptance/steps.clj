(ns veil.acceptance.steps
  "Step dispatch. Handlers live in one namespace per concept under
  veil.acceptance.steps.*; add a new concept's handlers to `step-handlers`."
  (:require [veil.acceptance.step-support :refer [fail]]
            [veil.acceptance.steps.window :as window]))

(def step-handlers
  (concat window/handlers))

(defn handle-step [world text]
  (if-let [[handler match] (some (fn [[pattern handler]]
                                   (when-let [match (re-matches pattern text)]
                                     [handler match]))
                                 step-handlers)]
    (handler world match)
    (fail (str "unsupported acceptance step: " text))))
