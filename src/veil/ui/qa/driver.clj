(ns veil.ui.qa.driver
  "Per-frame driver for scripted input."
  (:require [veil.ui.qa.play :as play]
            [veil.game.events :as events]))

(defn new-driver [steps]
  {:pending steps :tick 0})

(defn advance [handle driver state event]
  "Advance the driver by one frame, pressing any step due at this tick."
  (let [new-tick (inc (:tick driver))
        pending (:pending driver)
        step-due (and (seq pending) (= (:tick (first pending)) new-tick))]
    (if step-due
      (let [step (first pending)
            result (play/press handle state new-tick (:key step))
            new-pending (rest pending)
            finished? (empty? new-pending)]
        {:driver (assoc driver :pending new-pending :tick new-tick)
         :state (:state result)
         :entries (:entries result)
         :finished? finished?})
      (let [finished? (empty? pending)]
        {:driver (assoc driver :tick new-tick)
         :state state
         :entries []
         :finished? finished?}))))
