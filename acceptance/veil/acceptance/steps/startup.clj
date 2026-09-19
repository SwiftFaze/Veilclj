(ns veil.acceptance.steps.startup
  "Acceptance steps for startup, starting state, and draw commands."
  (:require [veil.acceptance.step-support :refer [ok check fail]]
            [veil.game.state :as state]
            [veil.ui.input :as input]
            [veil.ui.view :as view]
            [veil.mods.fixtures :as f]
            [veil.mods.loader :as loader]
            [veil.mods.disk :as disk]))

(def handlers
  [[#"the mods directory holds mod \"([^\"]+)\" with no dependencies"
    (fn [world [_ mod-id]]
      (swap! world assoc :mods-data (f/mods (f/manifest mod-id)))
      (ok))]

   [#"the mods directory holds mod \"([^\"]+)\" depending on \"([^\"]+)\""
    (fn [world [_ mod-id dep]]
      (swap! world assoc :mods-data (f/mods (f/manifest mod-id dep)))
      (ok))]

   [#"the mods directory holds mod \"([^\"]+)\" with widget files (.+) that are both missing their label"
    (fn [world [_ mod-id files-str]]
      (let [files (clojure.string/split files-str #" and ")
            file-specs (mapv (fn [f]
                              (clojure.string/replace f #"\"" ""))
                            files)
            widget-files (mapv (fn [f]
                                [f "{\"id\": \"core:widget\"}"])
                              file-specs)]
        (swap! world assoc :mods-data (apply f/mods (f/manifest mod-id) widget-files)))
      (ok))]

   [#"the mods are loaded for startup"
    (fn [world _]
      (let [mods-data (:mods-data @world)]
        (swap! world assoc :startup (loader/startup mods-data f/content-types)))
      (ok))]

   [#"startup has the registry"
    (fn [world _]
      (let [startup (:startup @world)
            has-registry (contains? startup :registry)]
        (check has-registry "startup has registry")))]

   [#"startup has no registry"
    (fn [world _]
      (let [startup (:startup @world)
            has-registry (contains? startup :registry)]
        (check (not has-registry) "startup has no registry")))]

   [#"startup has no error"
    (fn [world _]
      (let [startup (:startup @world)
            has-error (contains? startup :error)]
        (check (not has-error) "startup has no error")))]

   [#"the startup error starts with \"([^\"]+)\""
    (fn [world [_ prefix]]
      (let [startup (:startup @world)
            error (:error startup)]
        (check (.startsWith error prefix)
               (str "error starts with \"" prefix "\""))))]

   [#"the startup error names \"([^\"]+)\" and \"([^\"]+)\""
    (fn [world [_ name1 name2]]
      (let [startup (:startup @world)
            error (:error startup)]
        (check (and (.contains error name1) (.contains error name2))
               (str "error names both \"" name1 "\" and \"" name2 "\""))))]

   [#"the startup exit status is (\d+)"
    (fn [world [_ status-str]]
      (let [startup (:startup @world)
            expected (Integer/parseInt status-str)
            actual (:exit-status startup)]
        (check (= expected actual)
               (str "exit status is " actual))))]

   [#"a mods registry"
    (fn [world _]
      (swap! world assoc :registry {:load-order ["core"] :content {}})
      (ok))]

   [#"the starting state is built"
    (fn [world _]
      (let [registry (:registry @world)
            s (state/starting registry)]
        (swap! world assoc :state s))
      (ok))]

   [#"the starting screen is the main menu"
    (fn [world _]
      (let [s (:state @world)
            screen (state/screen s)]
        (check (= :main-menu screen) "screen is main menu")))]

   [#"the game is not over"
    (fn [world _]
      (let [s (:state @world)
            over (state/over? s)]
        (check (not over) "game is not over")))]

   [#"the starting state carries that registry"
    (fn [world _]
      (let [s (:state @world)
            registry (:registry @world)
            actual-registry (state/mods s)]
        (check (= registry actual-registry)
               "starting state carries registry")))]

   [#"a key event arrives with raw key (.+)"
    (fn [world [_ key-name]]
      (let [event (case key-name
                    "Escape" {:raw-key (char 27)}
                    "Enter" {:raw-key \newline}
                    "a" {:raw-key \a}
                    "no raw key" {}
                    (fail (str "unknown key: " key-name)))]
        (swap! world assoc :event event))
      (ok))]

   [#"the event is a raw Escape"
    (fn [world _]
      (let [event (:event @world)
            is-escape (input/escape? event)]
        (check is-escape "event is raw Escape")))]

   [#"the event is not a raw Escape"
    (fn [world _]
      (let [event (:event @world)
            is-escape (input/escape? event)]
        (check (not is-escape) "event is not raw Escape")))]

   [#"the main menu with ([^ ]+) selected"
    (fn [world [_ item]]
      (let [s (state/initial)
            s (loop [s s]
                (let [selected (state/selected-item s)]
                  (if (= selected item)
                    s
                    (state/handle-input s :down))))]
        (swap! world assoc :state s))
      (ok))]

   [#"the draw commands are built for a window (\d+) pixels wide"
    (fn [world [_ width-str]]
      (let [width (Integer/parseInt width-str)
            s (:state @world)
            commands (view/frame s width)]
        (swap! world assoc :draw-commands commands))
      (ok))]

   [#"the command for ([^ ]+) has the colour (\d+) (\d+) (\d+)"
    (fn [world [_ item r-str g-str b-str]]
      (let [expected-color [(Integer/parseInt r-str)
                           (Integer/parseInt g-str)
                           (Integer/parseInt b-str)]
            commands (:draw-commands @world)
            cmd (first (filter #(= item (:text %)) commands))]
        (check (and cmd (= expected-color (:color cmd)))
               (str "command for " item " has color " expected-color))))]

   [#"the command on row (\d+) is drawn at x (\d+) and y (\d+)"
    (fn [world [_ row-str x-str y-str]]
      (let [row (Integer/parseInt row-str)
            expected-x (Integer/parseInt x-str)
            expected-y (Integer/parseInt y-str)
            commands (:draw-commands @world)
            cmd (first (filter #(= row (:row %)) commands))]
        (check (and cmd (= expected-x (:x cmd)) (= expected-y (:y cmd)))
               (str "row " row " at (" expected-x " " expected-y ")"))))]

   [#"there is at least one command"
    (fn [world _]
      (let [commands (:draw-commands @world)]
        (check (seq commands) "at least one command")))]

   [#"every command has a colour, an x and a y"
    (fn [world _]
      (let [commands (:draw-commands @world)
            all-have-data (every? (fn [cmd]
                                   (and (contains? cmd :color)
                                        (contains? cmd :x)
                                        (contains? cmd :y)))
                                 commands)]
        (check all-have-data "every command has color, x, and y")))]

   [#"the game is on the ([^ ]+) screen"
    (fn [world [_ screen-name]]
      (let [s (case screen-name
                "main menu" (state/initial)
                "map" (-> (state/initial) (state/handle-input :confirm))
                "options" (-> (state/initial) (state/handle-input :down) (state/handle-input :confirm))
                (fail (str "unknown screen: " screen-name)))]
        (swap! world assoc :state s))
      (ok))]])
