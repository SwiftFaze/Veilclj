(ns veil.main
  "Entry point: opens the Quil window. Kept thin on purpose - game rules live
  in pure namespaces under veil.game, drawing under veil.ui. It calls Quil and
  passes data along; bb shell-check fails the build if it decides anything."
  (:require [quil.core :as q]
            [quil.middleware :as m]
            [quil.applet :as qa]
            [veil.game.state :as state]
            [veil.game.theme :as theme]
            [veil.ui.draw :as draw]
            [veil.ui.font :as font]
            [veil.ui.input :as input]
            [veil.ui.qa.files :as files]
            [veil.ui.qa.launch :as launch]
            [veil.ui.qa.mode :as mode]
            [veil.mods.disk :as disk]
            [veil.mods.fonts :as fonts]
            [veil.mods.loader :as loader]
            [veil.mods.themes :as themes])
  (:gen-class))

(def window
  {:title "Veil"
   :size  [960 600]})

(defn- setup [registry themes font-path size]
  (q/frame-rate 30)
  (q/text-font (q/create-font font-path size true))
  (state/starting registry themes))

(defn- prevent-processing-exit
  "Processing calls exit() if its key field == 27 (ESC). To make Esc mean 'back'
  instead, we zero that field. See processing.core.PApplet.handleKeyEvent."
  [event]
  (when (input/escape? event)
    (set! (.-key ^processing.core.PApplet (qa/current-applet)) (char 0)))
  event)

(defn- handle-key
  "The one path a key takes into the game, whether it was typed or scripted."
  [state event]
  (prevent-processing-exit event)
  (state/handle-input state (input/event->input event)))

(defn- update-state [qa-state state]
  (let [{:keys [qa entries exit?] new-state :state} (mode/frame @qa-state handle-key state)]
    (reset! qa-state qa)
    (files/append-log! (:log-path qa) entries)
    (when exit? (q/exit))
    new-state))

(defn- key-pressed [qa-state state event]
  (let [{:keys [entries] new-state :state} (mode/on-key @qa-state handle-key state (q/frame-count) event)]
    (files/append-log! (:log-path @qa-state) entries)
    new-state))

(defn- plan-step [{:keys [args]}]
  (mode/plan args files/read-script))

(defn- load-mods-step [_]
  (let [mods-dir (loader/mods-dir (System/getProperty "veil.mods.dir"))]
    (let [result (loader/startup (disk/read-mods-dir mods-dir) [themes/content-type fonts/content-type])]
      (if (:error result)
        result
        (assoc result :mods-dir mods-dir)))))

(defn- themes-step [{:keys [registry]}]
  (themes/startup registry theme/default-id))

(defn- fonts-step [{:keys [registry]}]
  (fonts/startup registry fonts/default-id))

(defn- open-font-step [{:keys [mods-dir font]}]
  (font/open mods-dir font))

(defn- start-log-step [{:keys [qa]}]
  (files/start-log! (:log-path qa)))

(defn- die [outcome]
  (binding [*out* *err*]
    (println (:error outcome)))
  (System/exit (:exit-status outcome)))

(defn- open-window [{:keys [registry themes font-path size qa]}]
  (let [qa-state (atom qa)]
    (q/sketch
      :title       (:title window)
      :size        (:size window)
      :setup       #(setup registry themes font-path size)
      :draw        draw/draw!
      :update      (fn [state] (update-state qa-state state))
      :key-pressed (fn [state event] (key-pressed qa-state state event))
      :features    [:exit-on-close :resizable]
      :middleware  [m/fun-mode])))

(defn -main [& args]
  (let [launched (launch/run-steps {:args args}
                                   [plan-step load-mods-step themes-step fonts-step open-font-step start-log-step])
        outcome (launch/outcome launched)]
    (if (:error outcome)
      (die outcome)
      (open-window (:start outcome)))))
