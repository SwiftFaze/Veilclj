(ns veil.acceptance.steps.themes
  "Acceptance steps for the themes feature."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [veil.acceptance.step-support :refer [ok check fail]]
            [veil.game.theme :as theme]
            [veil.game.state :as state]
            [veil.mods.themes :as themes]
            [veil.mods.fixtures :as fixtures]
            [veil.mods.loader :as loader]
            [veil.mods.registry :as registry]
            [veil.ui.view :as view]))

(defn- color-obj [r g b]
  {:r r :g g :b b})

(defn- get-content-types []
  (into fixtures/content-types [themes/content-type]))

(def handlers
  [[#"mod \"([^\"]+)\" has a theme \"([^\"]+)\" with every required color"
    (fn [world [_ mod id]]
      (let [theme-name (second (str/split id #":"))
            colors {:SELECTED_HIGHLIGHT (color-obj 1 2 3)
                    :SELECTED_TEXT (color-obj 4 5 6)
                    :NORMAL_TEXT (color-obj 7 8 9)
                    :DIMMED_TEXT (color-obj 10 11 12)
                    :BACKGROUND (color-obj 13 14 15)
                    :INVALID_HIGHLIGHT (color-obj 16 17 18)
                    :VALID_HIGHLIGHT (color-obj 19 20 21)
                    :TABLE_HEADER_BACKGROUND (color-obj 22 23 24)
                    :BORDER (color-obj 25 26 27)
                    :SCROLLBAR_THUMB (color-obj 28 29 30)
                    :ACCENT (color-obj 31 32 33)
                    :WINDOW_BORDER (color-obj 34 35 36)
                    :TABLE_HEADER_TEXT (color-obj 37 38 39)}
            theme-data {:id id :colors colors}
            path (str mod "/themes/" theme-name ".json")]
        (swap! world assoc-in [:files path] (json/write-str theme-data))
        (swap! world assoc :current-theme {:mod mod :id id :path path :data theme-data})
        (ok)))]

   [#"mod \"([^\"]+)\" has a theme \"([^\"]+)\" with every required color except \"([^\"]+)\""
    (fn [world [_ mod id missing-key]]
      (let [theme-name (second (str/split id #":"))
            all-keys [:SELECTED_HIGHLIGHT :SELECTED_TEXT :NORMAL_TEXT :DIMMED_TEXT :BACKGROUND
                      :INVALID_HIGHLIGHT :VALID_HIGHLIGHT :TABLE_HEADER_BACKGROUND :BORDER
                      :SCROLLBAR_THUMB :ACCENT :WINDOW_BORDER :TABLE_HEADER_TEXT]
            kept-keys (filterv #(not= (name %) missing-key) all-keys)
            colors (into {} (map-indexed (fn [i k] [k (color-obj (inc i) (+ i 2) (+ i 3))]) kept-keys))
            theme-data {:id id :colors colors}
            path (str mod "/themes/" theme-name ".json")]
        (swap! world assoc-in [:files path] (json/write-str theme-data))
        (swap! world assoc :current-theme {:mod mod :id id :path path :data theme-data})
        (ok)))]

   [#"mod \"([^\"]+)\" has a theme \"([^\"]+)\" with every required color that overrides \"([^\"]+)\""
    (fn [world [_ mod id overrides]]
      (let [theme-name (second (str/split id #":"))
            colors {:SELECTED_HIGHLIGHT (color-obj 1 2 3)
                    :SELECTED_TEXT (color-obj 4 5 6)
                    :NORMAL_TEXT (color-obj 7 8 9)
                    :DIMMED_TEXT (color-obj 10 11 12)
                    :BACKGROUND (color-obj 13 14 15)
                    :INVALID_HIGHLIGHT (color-obj 16 17 18)
                    :VALID_HIGHLIGHT (color-obj 19 20 21)
                    :TABLE_HEADER_BACKGROUND (color-obj 22 23 24)
                    :BORDER (color-obj 25 26 27)
                    :SCROLLBAR_THUMB (color-obj 28 29 30)
                    :ACCENT (color-obj 31 32 33)
                    :WINDOW_BORDER (color-obj 34 35 36)
                    :TABLE_HEADER_TEXT (color-obj 37 38 39)}
            theme-data {:id id :colors colors :overrides overrides}
            path (str mod "/themes/" theme-name ".json")]
        (swap! world assoc-in [:files path] (json/write-str theme-data))
        (swap! world assoc :current-theme {:mod mod :id id :path path :data theme-data})
        (ok)))]

   [#"mod \"([^\"]+)\" has the theme file \"([^\"]+)\" ported from Java Veil"
    (fn [world [_ mod path]]
      (let [shipped-path (str "mods/" path)
            content (slurp shipped-path)]
        (swap! world assoc-in [:files path] content)
        (swap! world assoc :current-theme {:mod mod :path path :data (json/read-str content :key-fn keyword)})
        (ok)))]

   [#"mod \"([^\"]+)\" has the theme file \"([^\"]+)\" containing (.+)"
    (fn [world [_ mod path content]]
      (swap! world assoc-in [:files path] content)
      (try
        (swap! world assoc :current-theme {:mod mod :path path :data (json/read-str content :key-fn keyword)})
        (catch Exception _
          (swap! world assoc :current-theme {:mod mod :path path :data nil})))
      (ok))]

   [#"mod \"([^\"]+)\" ships no themes"
    (fn [world [_ mod]]
      (ok))]

   [#"its ([A-Z_]+) is (\d+),(\d+),(\d+)"
    (fn [world [_ key-name r g b]]
      (let [key (keyword key-name)
            current (:current-theme @world)
            colors (get-in current [:data :colors] {})
            new-colors (assoc colors key (color-obj (Integer/parseInt r) (Integer/parseInt g) (Integer/parseInt b)))]
        (swap! world update :current-theme assoc-in [:data :colors] new-colors)
        (when-let [path (:path current)]
          (swap! world assoc-in [:files path] (json/write-str (get-in @world [:current-theme :data])))))
      (ok))]

   [#"its BORDER red channel is (.+)"
    (fn [world [_ channel-str]]
      (let [current (:current-theme @world)
            val (try (Long/parseLong channel-str) (catch Exception _ (try (Double/parseDouble channel-str) (catch Exception _ channel-str))))
            colors (get-in current [:data :colors] {})
            border (or (get colors :BORDER) {:r 0 :g 0 :b 0})
            new-colors (assoc colors :BORDER (assoc border :r val))]
        (swap! world update :current-theme assoc-in [:data :colors] new-colors)
        (when-let [path (:path current)]
          (swap! world assoc-in [:files path] (json/write-str (get-in @world [:current-theme :data])))))
      (ok))]

   [#"its BORDER color is (.+)"
    (fn [world [_ color-str]]
      (let [current (:current-theme @world)
            val (try (json/read-str color-str :key-fn keyword) (catch Exception _ color-str))
            colors (get-in current [:data :colors] {})
            new-colors (assoc colors :BORDER val)]
        (swap! world update :current-theme assoc-in [:data :colors] new-colors)
        (when-let [path (:path current)]
          (swap! world assoc-in [:files path] (json/write-str (get-in @world [:current-theme :data])))))
      (ok))]

   [#"it also defines the color \"([^\"]+)\""
    (fn [world [_ key-name]]
      (let [key (keyword key-name)
            current (:current-theme @world)
            colors (get-in current [:data :colors] {})
            new-colors (assoc colors key (color-obj 100 100 100))]
        (swap! world update :current-theme assoc-in [:data :colors] new-colors)
        (when-let [path (:path current)]
          (swap! world assoc-in [:files path] (json/write-str (get-in @world [:current-theme :data])))))
      (ok))]

   [#"it also defines ([A-Z_]+) as (\d+),(\d+),(\d+)"
    (fn [world [_ key-name r g b]]
      (let [key (keyword key-name)
            current (:current-theme @world)
            colors (get-in current [:data :colors] {})
            new-colors (assoc colors key (color-obj (Integer/parseInt r) (Integer/parseInt g) (Integer/parseInt b)))]
        (swap! world update :current-theme assoc-in [:data :colors] new-colors)
        (when-let [path (:path current)]
          (swap! world assoc-in [:files path] (json/write-str (get-in @world [:current-theme :data])))))
      (ok))]

   [#"theme \"([^\"]+)\" is registered from mod \"([^\"]+)\""
    (fn [world [_ id mod]]
      (let [content-types (get-content-types)
            folders (set (map #(first (str/split % #"/")) (keys (:files @world))))
            mods-data {:folders folders :files (:files @world)}
            load-result (loader/load-mods mods-data content-types)
            registry (:registry load-result)
            entry (and registry (registry/entry registry :theme id))]
        (if registry
          (check (and entry (= mod (:mod entry)))
                 (str "theme " id " is registered from mod " mod))
          (fail (str "loading failed:\n" (loader/error-report (:errors load-result)))))))]

   [#"theme \"([^\"]+)\" has ([A-Z_]+) (\d+),(\d+),(\d+)"
    (fn [world [_ id key-name r g b]]
      (let [expected [(Integer/parseInt r) (Integer/parseInt g) (Integer/parseInt b)]
            content-types (get-content-types)
            folders (set (map #(first (str/split % #"/")) (keys (:files @world))))
            mods-data {:folders folders :files (:files @world)}
            load-result (loader/load-mods mods-data content-types)
            registry (:registry load-result)
            entry (and registry (registry/entry registry :theme id))
            key (keyword key-name)
            actual (and entry (get (:value entry) key))]
        (if registry
          (check (= expected actual)
                 (str "theme " id " has " key-name " " expected ", got " actual))
          (fail (str "loading failed:\n" (loader/error-report (:errors load-result)))))))]

   [#"the starting state is built from the loaded mods"
    (fn [world _]
      (try
        (let [content-types (get-content-types)
              folders (set (map #(first (str/split % #"/")) (keys (:files @world))))
              mods-data {:folders folders :files (:files @world)}
              load-result (loader/load-mods mods-data content-types)]
          (if-let [registry (:registry load-result)]
            (let [themes-result (themes/startup registry theme/default-id)]
              (if-let [error (:error themes-result)]
                (do
                  (swap! world assoc :startup-error error)
                  (swap! world dissoc :state))
                (do
                  (swap! world assoc :state (state/starting registry (:themes themes-result)))
                  (swap! world dissoc :startup-error))))
            (do
              (swap! world assoc :startup-error (loader/error-report (:errors load-result)))
              (swap! world dissoc :state))))
        (catch Exception e
          (swap! world assoc :startup-error (str "Exception: " (.getMessage e)))
          (swap! world dissoc :state)))
      (ok))]

   [#"the active theme is \"([^\"]+)\""
    (fn [world [_ id]]
      (let [s (:state @world)]
        (check (and s (= id (theme/active-id s)))
               (str "active theme is " id))))]

   [#"the color for ([A-Z_]+) is (\d+),(\d+),(\d+)"
    (fn [world [_ key-name r g b]]
      (let [expected [(Integer/parseInt r) (Integer/parseInt g) (Integer/parseInt b)]
            s (:state @world)
            key (keyword key-name)
            actual (try (theme/color s key) (catch Exception _ nil))]
        (check (= expected actual)
               (str "color for " key-name " is " expected ", got " actual))))]

   [#"theme \"([^\"]+)\" is made active"
    (fn [world [_ id]]
      (try
        (swap! world update :state theme/activate id)
        (ok)
        (catch Exception e
          (fail (str "Could not activate theme " id ": " (.getMessage e))))))]

   [#"starting fails naming the missing theme \"([^\"]+)\""
    (fn [world [_ id]]
      (let [error (:startup-error @world)]
        (check (and error (.contains error id))
               (str "starting fails naming the missing theme " id))))]

   [#"the frame's background color is (\d+),(\d+),(\d+)"
    (fn [world [_ r g b]]
      (let [expected [(Integer/parseInt r) (Integer/parseInt g) (Integer/parseInt b)]
            s (:state @world)
            actual (try (view/background s) (catch Exception _ nil))]
        (check (= expected actual)
               (str "frame's background color is " expected ", got " actual))))]

   [#"the selected menu item \"([^\"]+)\" is drawn in (\d+),(\d+),(\d+)"
    (fn [world [_ label r g b]]
      (let [expected [(Integer/parseInt r) (Integer/parseInt g) (Integer/parseInt b)]
            s (:state @world)
            commands (try (view/frame s 960) (catch Exception _ []))
            cmd (first (filter #(and (= label (:text %)) (:selected? %)) commands))
            actual (:color cmd)]
        (check (and cmd (= expected actual))
               (str "selected item " label " is drawn in " expected ", got " actual))))]

   [#"the menu item \"([^\"]+)\" is drawn in (\d+),(\d+),(\d+)"
    (fn [world [_ label r g b]]
      (let [expected [(Integer/parseInt r) (Integer/parseInt g) (Integer/parseInt b)]
            s (:state @world)
            commands (try (view/frame s 960) (catch Exception _ []))
            cmd (first (filter #(and (= label (:text %)) (not (:selected? %))) commands))
            actual (:color cmd)]
        (check (and cmd (= expected actual))
               (str "item " label " is drawn in " expected ", got " actual))))]
   ])
