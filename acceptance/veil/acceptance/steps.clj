(ns veil.acceptance.steps
  "Step dispatch. Handlers live in one namespace per concept under
  veil.acceptance.steps.*; add a new concept's handlers to `step-handlers`."
  (:require [veil.acceptance.step-support :refer [fail]]
            [veil.acceptance.steps.window :as window]
            [veil.acceptance.steps.menu :as menu]
            [veil.acceptance.steps.keyboard-input :as keyboard-input]
            [veil.acceptance.steps.mods :as mods]
            [veil.acceptance.steps.qa :as qa]
            [veil.acceptance.steps.docs-check :as docs-check]
            [veil.acceptance.steps.crap-gate :as crap-gate]
            [veil.acceptance.steps.startup :as startup]
            [veil.acceptance.steps.shell-check :as shell-check]
            [veil.acceptance.steps.themes :as themes]
            [veil.acceptance.steps.fonts :as fonts]
            [veil.acceptance.steps.grid :as grid]))

(def step-handlers
  (concat window/handlers keyboard-input/handlers menu/handlers mods/handlers qa/handlers docs-check/handlers crap-gate/handlers startup/handlers shell-check/handlers themes/handlers fonts/handlers grid/handlers))

(defn handle-step [world text]
  (if-let [[handler match] (some (fn [[pattern handler]]
                                   (when-let [match (re-matches pattern text)]
                                     [handler match]))
                                 step-handlers)]
    (handler world match)
    (fail (str "unsupported acceptance step: " text))))
