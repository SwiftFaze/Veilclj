(ns veil.main
  "Entry point: opens the Quil window. Kept thin on purpose - game rules live
  in pure namespaces under veil.game, drawing under veil.ui."
  (:require [quil.core :as q]
            [quil.middleware :as m]
            [quil.applet :as qa]
            [veil.game.state :as state]
            [veil.game.events :as events]
            [veil.ui.draw :as draw]
            [veil.ui.input :as input]
            [veil.ui.qa.script :as script]
            [veil.mods.disk :as disk]
            [veil.mods.loader :as loader]
            [veil.ui.qa.launch :as launch]
            [veil.ui.qa.files :as files]
            [veil.ui.qa.driver :as driver])
  (:gen-class))

(def window
  {:title "Veil"
   :size  [960 600]})

(defn- setup [registry]
  (q/frame-rate 30)
  (q/text-font (q/create-font "Monospaced" 32))
  (state/with-mods (state/initial) registry))

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

(defn- update-state-qa [qa-state state]
  (if-let [d (:driver @qa-state)]
    (let [result (driver/advance key-pressed d state nil)]
      (when (:entries result)
        (files/append-log! (:log-path @qa-state) (:entries result)))
      (swap! qa-state assoc :driver (:driver result))
      (if (or (:finished? result) (state/over? (:state result)))
        (do (q/exit) (:state result))
        (:state result)))
    (if (state/over? state)
      (do (q/exit) state)
      state)))

(defn- key-pressed-qa [qa-state state event]
  (prevent-processing-exit event)
  (let [new-state (key-pressed state event)]
    (if-let [log-path (:log-path @qa-state)]
      (when-not (:driver @qa-state)
        (let [key-kw (script/event->key-keyword event)
              events-list (events/between state new-state)]
          (files/append-log! log-path (concat [{:tick (q/frame-count) :key key-kw}]
                                               (map #(assoc % :tick (q/frame-count)) events-list))))))
    new-state))

(defn -main [& args]
  (let [launch-result (launch/parse-args args)]
    (when (:error launch-result)
      (binding [*out* *err*]
        (println (:error launch-result)))
      (System/exit 1))

    (let [script-result (when (:keys launch-result) (files/read-script (:keys launch-result)))]
      (when (and (:keys launch-result) (:error script-result))
        (binding [*out* *err*]
          (println (:error script-result)))
        (System/exit 1))

      (when (:log launch-result)
        (try
          (files/start-log! (:log launch-result))
          (catch Exception e
            (binding [*out* *err*]
              (println (.getMessage e)))
            (System/exit 1))))

      (let [qa-state (atom {:driver (when (:keys launch-result)
                                       (driver/new-driver (:steps script-result)))
                            :log-path (:log launch-result)})
            load-result (loader/load-mods (disk/read-mods-dir "mods") [])]
        (if (contains? load-result :errors)
          (do
            (binding [*out* *err*]
              (println (loader/error-report (:errors load-result))))
            (System/exit 1))
          (q/sketch
            :title      (:title window)
            :size       (:size window)
            :setup      #(setup (:registry load-result))
            :draw       draw/draw!
            :update     (fn [state] (update-state-qa qa-state state))
            :key-pressed (fn [state event] (key-pressed-qa qa-state state event))
            :features   [:exit-on-close]
            :middleware [m/fun-mode]))))))
