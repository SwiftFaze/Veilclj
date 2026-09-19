(ns veil.acceptance.steps.startup
  "Acceptance steps for startup, starting state, and draw commands."
  (:require [clojure.string :as str]
            [veil.acceptance.step-support :refer [ok check fail]]
            [veil.game.state :as state]
            [veil.game.theme :as theme]
            [veil.ui.input :as input]
            [veil.ui.view :as view]
            [veil.ui.qa.launch :as launch]
            [veil.ui.qa.mode :as mode]
            [veil.mods.fixtures :as f]
            [veil.mods.loader :as loader]
            [veil.mods.themes :as themes]))

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
      (let [files (str/split files-str #" and ")
            file-specs (mapv (fn [f]
                              (str/replace f #"\"" ""))
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
        (check (and error (.startsWith error prefix))
               (str "error starts with \"" prefix "\""))))]

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

   [#"the starting state carries that registry"
    (fn [world _]
      (let [s (:state @world)
            registry (:registry @world)
            actual-registry (state/mods s)]
        (check (= registry actual-registry)
               "starting state carries registry")))]

   [#"a key event arrives with raw key (.+)"
    (fn [world [_ key-name]]
      (if-let [event (get {"Escape" {:raw-key (char 27)}
                           "Enter" {:raw-key \newline}
                           "a" {:raw-key \a}
                           "no raw key" {}}
                          key-name)]
        (do (swap! world assoc :event event)
            (ok))
        (fail (str "unknown key: " key-name))))]

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

   [#"the main menu with (.+) selected"
    (fn [world [_ item]]
      (let [base-state (state/starting {:load-order ["core"] :content {}}
                                       {"core:default" {:SELECTED_HIGHLIGHT [192 192 192]
                                                         :SELECTED_TEXT [0 0 0]
                                                         :NORMAL_TEXT [255 255 255]
                                                         :DIMMED_TEXT [128 128 128]
                                                         :BACKGROUND [0 0 0]
                                                         :INVALID_HIGHLIGHT [224 90 78]
                                                         :VALID_HIGHLIGHT [111 207 125]
                                                         :TABLE_HEADER_BACKGROUND [26 26 26]
                                                         :BORDER [192 192 192]
                                                         :SCROLLBAR_THUMB [128 128 128]
                                                         :ACCENT [238 179 146]
                                                         :WINDOW_BORDER [255 255 255]
                                                         :TABLE_HEADER_TEXT [0 194 194]
                                                         :SUCCESS [111 207 125]
                                                         :ERROR [224 90 78]
                                                         :WARNING [238 179 146]
                                                         :INFO [238 179 146]
                                                         :FOCUSED_BORDER [238 179 146]
                                                         :SHADOW [0 0 0]}})
            reached (->> (iterate #(state/handle-input % :down) base-state)
                         (take (count (state/menu-items base-state)))
                         (filter #(= item (state/selected-item %)))
                         first)]
        (if reached
          (do (swap! world assoc :state reached)
              (ok))
          (fail (str "no menu item " item)))))]

   [#"the draw commands are built for a window (\d+) pixels wide"
    (fn [world [_ width-str]]
      (let [width (Integer/parseInt width-str)
            s (:state @world)
            commands (view/frame s width)]
        (swap! world assoc :draw-commands commands))
      (ok))]

   [#"the command for \"?([^\"]+?)\"? has the colour (\d+) (\d+) (\d+)"
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

   [#"the game is on the (.+) screen"
    (fn [world [_ screen-name]]
      (let [start (state/starting {:load-order ["core"] :content {}}
                                  {"core:default" {:SELECTED_HIGHLIGHT [192 192 192]
                                                    :SELECTED_TEXT [0 0 0]
                                                    :NORMAL_TEXT [255 255 255]
                                                    :DIMMED_TEXT [128 128 128]
                                                    :BACKGROUND [0 0 0]
                                                    :INVALID_HIGHLIGHT [224 90 78]
                                                    :VALID_HIGHLIGHT [111 207 125]
                                                    :TABLE_HEADER_BACKGROUND [26 26 26]
                                                    :BORDER [192 192 192]
                                                    :SCROLLBAR_THUMB [128 128 128]
                                                    :ACCENT [238 179 146]
                                                    :WINDOW_BORDER [255 255 255]
                                                    :TABLE_HEADER_TEXT [0 194 194]
                                                    :SUCCESS [111 207 125]
                                                    :ERROR [224 90 78]
                                                    :WARNING [238 179 146]
                                                    :INFO [238 179 146]
                                                    :FOCUSED_BORDER [238 179 146]
                                                    :SHADOW [0 0 0]}})
            screens {"main menu" start
                     "map" (state/handle-input start :confirm)
                     "options" (-> start (state/handle-input :down) (state/handle-input :confirm))}]
        (if-let [s (get screens screen-name)]
          (do (swap! world assoc :state s)
              (ok))
          (fail (str "unknown screen: " screen-name)))))]

   [#"the game is started with arguments \"(.*)\""
    (fn [world [_ args-str]]
      (let [args (if (str/blank? args-str) [] (str/split args-str #" "))
            plan-step (fn [{:keys [args]}] (mode/plan args (constantly {:steps []})))
            load-step (fn [_] (loader/startup (:mods-data @world) f/content-types))
            launched (launch/run-steps {:args args} [plan-step load-step])]
        (swap! world assoc :launched (launch/outcome launched)))
      (ok))]

   [#"the game does not start"
    (fn [world _]
      (let [outcome (:launched @world)
            has-error (contains? outcome :error)]
        (check has-error "game does not start")))]

   [#"the game starts"
    (fn [world _]
      (let [outcome (:launched @world)
            started (contains? outcome :start)]
        (check started "game starts")))]

   [#"the game starts with the mods registry"
    (fn [world _]
      (let [expected (:registry (loader/startup (:mods-data @world) f/content-types))
            actual (get-in @world [:launched :start :registry])]
        (check (and (some? expected) (= expected actual))
               (str "game starts with the mods registry, got " (pr-str actual)))))]

   [#"the launch message starts with \"([^\"]+)\""
    (fn [world [_ prefix]]
      (let [outcome (:launched @world)
            error (:error outcome)]
        (check (and error (.startsWith error prefix))
               (str "launch message starts with \"" prefix "\""))))]

   [#"the launch exit status is (\d+)"
    (fn [world [_ status-str]]
      (let [outcome (:launched @world)
            expected (Integer/parseInt status-str)
            actual (:exit-status outcome)]
        (check (= expected actual)
               (str "launch exit status is " actual))))]

   [#"the startup error names \"([^\"]+)\" and \"([^\"]+)\""
    (fn [world [_ name1 name2]]
      (let [startup (:startup @world)
            error (:error startup)]
        (check (and error (.contains error name1) (.contains error name2))
               (str "error names both \"" name1 "\" and \"" name2 "\""))))]

   [#"the startup error names \"([^\"]+)\""
    (fn [world [_ name]]
      (let [startup (:startup @world)
            error (:error startup)]
        (check (and error (.contains error name))
               (str "error names \"" name "\""))))]
   ])
