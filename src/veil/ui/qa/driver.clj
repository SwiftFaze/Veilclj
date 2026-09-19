(ns veil.ui.qa.driver
  "Per-frame driver for scripted input."
  (:require [veil.ui.qa.play :as play]))

(defn new-driver [steps]
  {:pending steps :tick 0})

(defn- due? [pending tick]
  (and (seq pending) (= (:tick (first pending)) tick)))

(defn advance
  "Advance the driver by one frame, pressing any step due at this tick.
   Returns {:driver d :state s :entries [...] :finished? bool}."
  [handle driver state]
  (let [tick (inc (:tick driver))
        pending (:pending driver)]
    (if (due? pending tick)
      (let [pressed (play/press handle state tick (:key (first pending)))
            remaining (drop 1 pending)]
        {:driver (assoc driver :pending remaining :tick tick)
         :state (:state pressed)
         :entries (:entries pressed)
         :finished? (empty? remaining)})
      {:driver (assoc driver :tick tick)
       :state state
       :entries []
       :finished? (empty? pending)})))
