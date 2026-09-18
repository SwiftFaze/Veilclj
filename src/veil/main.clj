(ns veil.main
  "Entry point: opens the Quil window. Kept thin on purpose - game rules live
  in pure namespaces under veil.game, drawing under veil.ui."
  (:require [quil.core :as q]
            [quil.middleware :as m])
  (:gen-class))

(def window
  {:title "Veil"
   :size  [960 600]})

(defn- setup []
  (q/frame-rate 30)
  (q/text-font (q/create-font "Monospaced" 32))
  {})

(defn- draw [_state]
  (q/background 0)
  (q/fill 220)
  (q/text-align :center :center)
  (q/text (:title window) (/ (q/width) 2) (/ (q/height) 2)))

(defn -main [& _args]
  (q/sketch
    :title      (:title window)
    :size       (:size window)
    :setup      setup
    :draw       draw
    :features   [:exit-on-close]
    :middleware [m/fun-mode]))
