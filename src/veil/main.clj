(ns veil.main
  "Entry point: opens the Quil window. Kept thin on purpose - game rules live
  in pure namespaces under veil.game, drawing under veil.ui."
  (:require [quil.core :as q]
            [quil.middleware :as m]
            [quil.applet :as qa]
            [veil.game.state :as state]
            [veil.ui.draw :as draw]
            [veil.ui.input :as input])
  (:gen-class))

(def window
  {:title "Veil"
   :size  [960 600]})

(defn- setup []
  (q/frame-rate 30)
  (q/text-font (q/create-font "Monospaced" 32))
  (state/initial))

(defn- prevent-processing-exit
  "Processing calls exit() if its key field == 27 (ESC). To make Esc mean 'back'
  instead, we zero that field. See processing.core.PApplet.handleKeyEvent."
  [event]
  (when (= (char 27) (:raw-key event))
    (set! (.-key ^processing.core.PApplet (qa/current-applet)) (char 0)))
  event)

(defn- update-state [state]
  (if (state/over? state)
    (do
      (q/exit)
      state)
    state))

(defn- key-pressed [state event]
  (prevent-processing-exit event)
  (state/handle-input state (input/event->input event)))

(defn -main [& _args]
  (q/sketch
    :title      (:title window)
    :size       (:size window)
    :setup      setup
    :draw       draw/draw!
    :update     update-state
    :key-pressed key-pressed
    :features   [:exit-on-close]
    :middleware [m/fun-mode]))
