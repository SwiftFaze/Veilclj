(ns veil.mods.themes
  "Theme content type: named sets of UI colors shipped by mods.
   A theme is a JSON object with an id (namespace:name), a required
   colors object containing 13 color keys (each with r, g, b channels from 0-255),
   and optionally 6 more color keys that fall back to specific required keys.
   The :construct function resolves fallbacks, so a registry entry's :value is
   always complete (all 19 keys present as [r g b] vectors)."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [veil.mods.ids :as ids]
            [veil.mods.loader :as loader]
            [veil.mods.validate :as validate]
            [veil.mods.registry :as registry]))

(defn channel?
  "An integer from 0 to 255."
  [x]
  (and (int? x) (>= x 0) (<= x 255)))

(s/def ::channel (s/and int? channel?))

(s/def :color/r ::channel)
(s/def :color/g ::channel)
(s/def :color/b ::channel)
(s/def ::color (s/keys :req-un [:color/r :color/g :color/b]))

(s/def :theme.color/SELECTED_HIGHLIGHT ::color)
(s/def :theme.color/SELECTED_TEXT ::color)
(s/def :theme.color/NORMAL_TEXT ::color)
(s/def :theme.color/DIMMED_TEXT ::color)
(s/def :theme.color/BACKGROUND ::color)
(s/def :theme.color/INVALID_HIGHLIGHT ::color)
(s/def :theme.color/VALID_HIGHLIGHT ::color)
(s/def :theme.color/TABLE_HEADER_BACKGROUND ::color)
(s/def :theme.color/BORDER ::color)
(s/def :theme.color/SCROLLBAR_THUMB ::color)
(s/def :theme.color/ACCENT ::color)
(s/def :theme.color/WINDOW_BORDER ::color)
(s/def :theme.color/TABLE_HEADER_TEXT ::color)

; Optional keys with fallbacks
(s/def :theme.color/SUCCESS ::color)
(s/def :theme.color/ERROR ::color)
(s/def :theme.color/WARNING ::color)
(s/def :theme.color/INFO ::color)
(s/def :theme.color/FOCUSED_BORDER ::color)
(s/def :theme.color/SHADOW ::color)

(s/def ::colors (s/keys
  :req-un [:theme.color/SELECTED_HIGHLIGHT
           :theme.color/SELECTED_TEXT
           :theme.color/NORMAL_TEXT
           :theme.color/DIMMED_TEXT
           :theme.color/BACKGROUND
           :theme.color/INVALID_HIGHLIGHT
           :theme.color/VALID_HIGHLIGHT
           :theme.color/TABLE_HEADER_BACKGROUND
           :theme.color/BORDER
           :theme.color/SCROLLBAR_THUMB
           :theme.color/ACCENT
           :theme.color/WINDOW_BORDER
           :theme.color/TABLE_HEADER_TEXT]
  :opt-un [:theme.color/SUCCESS
           :theme.color/ERROR
           :theme.color/WARNING
           :theme.color/INFO
           :theme.color/FOCUSED_BORDER
           :theme.color/SHADOW]))

(s/def ::theme (s/keys :req-un [:veil.mods.ids/id ::colors]
                       :opt-un [:veil.mods.ids/overrides]))

(def phrases
  {::colors "an object with r, g and b"})

(defn- color->vector [{:keys [r g b]}]
  [r g b])

(defn- resolve-optional [colors optional-key fallback-key]
  (if (contains? colors optional-key)
    (get colors optional-key)
    (get colors fallback-key)))

(defn construct
  "Transform validated theme data into a registry entry value:
   all 19 color keys as [r g b] vectors, with optional keys filled from fallbacks."
  [data]
  (let [colors (:colors data)
        resolved-colors (into {}
          (for [[k v] colors]
            [(keyword (name k)) (color->vector v)]))]
    ; Add required keys first (should already be there)
    (merge resolved-colors
      ; Add optional keys with fallbacks
      {:SUCCESS (resolve-optional resolved-colors :SUCCESS :VALID_HIGHLIGHT)
       :ERROR (resolve-optional resolved-colors :ERROR :INVALID_HIGHLIGHT)
       :WARNING (resolve-optional resolved-colors :WARNING :ACCENT)
       :INFO (resolve-optional resolved-colors :INFO :ACCENT)
       :FOCUSED_BORDER (resolve-optional resolved-colors :FOCUSED_BORDER :ACCENT)
       :SHADOW (resolve-optional resolved-colors :SHADOW :BACKGROUND)})))

(def content-type
  {:type :theme
   :folder "themes"
   :spec ::theme
   :phrases phrases
   :construct construct})

(defn all
  "Map of theme-id to resolved colors for every registered theme."
  [registry]
  (into {}
    (for [theme-id (registry/content-ids registry :theme)]
      [theme-id (:value (registry/entry registry :theme theme-id))])))

(defn startup
  "Load themes from a registry and return {:themes {...}} when the default
   theme is registered, else {:error msg :exit-status 1}."
  [registry default-id]
  (if (registry/entry registry :theme default-id)
    {:themes (all registry)}
    {:error (loader/error-report [{:message (str "theme \"" default-id "\" is not registered")}])
     :exit-status 1}))
