(ns veil.mods.fonts
  "Font content type: named font files (.ttf or .otf) shipped by mods with
   a pixel size to render them at.
   A font is a JSON object with an id (namespace:name), a required file name
   (a bare .ttf or .otf, no path separators), and a required size (positive integer).
   The game draws in one font, always core:default.
   veil.mods.ids is required only to register the :veil.mods.ids/id spec that
   ::font refers to."
  (:require [clojure.spec.alpha :as s]
            [veil.mods.ids]
            [veil.mods.loader :as loader]
            [veil.mods.registry :as registry]
            [veil.mods.validate :as validate]))

(def default-id "core:default")

(defn font-file-name?
  "A string with no path separators that ends in .ttf or .otf."
  [x]
  (and (string? x)
       (not (or (.contains x "/") (.contains x "\\")))
       (not (.startsWith x "/"))
       (or (.endsWith x ".ttf") (.endsWith x ".otf"))))

(s/def ::file font-file-name?)
(s/def ::size pos-int?)

(s/def ::font (s/keys :req-un [:veil.mods.ids/id ::file ::size]
                      :opt-un [:veil.mods.ids/overrides]))

(def phrases
  {::file "a font file name ending in .ttf or .otf"
   ::size "a positive integer"})

(defn construct
  "Transform validated font data into a registry entry value: file name and size only."
  [data]
  (select-keys data [:file :size]))

(defn check
  "Validate that the font file exists in the same mod's fonts folder."
  [{:keys [data mod file paths]}]
  (let [font-file (:file data)
        expected-path (str mod "/fonts/" font-file)]
    (if (contains? paths expected-path)
      []
      [(validate/field-error file "/file"
                             (str "the font file \"" font-file "\" in \"" mod "/fonts\""))])))

(def content-type
  {:type :font
   :folder "fonts"
   :spec ::font
   :phrases phrases
   :construct construct
   :check check})

(defn font
  "Registered font entry: {:file \"<mod>/fonts/<name>.ttf\" :size 20} or nil.
   The file path is relative to the mods directory."
  [registry id]
  (when-let [entry (registry/entry registry :font id)]
    (let [value (:value entry)
          mod (:mod entry)]
      (assoc value :file (str mod "/fonts/" (:file value))))))

(defn- missing-default-report [default-id]
  (loader/error-report [{:message (str "font \"" default-id "\" is not registered")}]))

(defn startup
  "Load fonts from a registry and return {:font {...}} when the default font
   is registered, else {:error msg :exit-status 1}."
  [registry default-id]
  (if-let [entry (registry/entry registry :font default-id)]
    {:font (font registry default-id)}
    {:error (missing-default-report default-id) :exit-status 1}))
