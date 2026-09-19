(ns veil.game.theme
  "Theme color lookup: resolves color keys from the active theme in state.
   Pure functions with no I/O and no Quil dependency.")

(def default-id "core:default")

(defn color
  "Look up a color by key in the active theme. Throws ex-info if the key
   is not in the active theme's map, or if the active theme isn't in :themes."
  [state key]
  (let [theme-id (:active-theme state)
        themes (:themes state)
        theme-colors (get themes theme-id)]
    (when (nil? theme-colors)
      (throw (ex-info (str "active theme \"" theme-id "\" is not in state")
                      {:theme-id theme-id})))
    (when (nil? (get theme-colors key))
      (throw (ex-info (str "color key " (name key) " is not in theme")
                      {:key key})))
    (get theme-colors key)))

(defn active-id
  "The id of the currently active theme."
  [state]
  (:active-theme state))

(defn activate
  "Set the active theme to the given id. Throws ex-info if the id is not
   in the themes map."
  [state theme-id]
  (when (nil? (get (:themes state) theme-id))
    (throw (ex-info (str "theme \"" theme-id "\" is not in state")
                    {:theme-id theme-id})))
  (assoc state :active-theme theme-id))
