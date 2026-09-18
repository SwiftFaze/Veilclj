(ns veil.mods.fixtures
  "Content types and mods/ data shared by the veil.mods specs. The real content
  types arrive with #8; these exist only to exercise the machinery."
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [veil.mods.ids]))

(s/def :widget/label string?)
(s/def :rgb/r int?)
(s/def :rgb/g int?)
(s/def :rgb/b int?)
(s/def ::rgb (s/keys :req-un [:rgb/r :rgb/g :rgb/b]))
(s/def :widget/colors (s/map-of keyword? ::rgb))
(s/def ::widget (s/keys :req-un [:veil.mods.ids/id :widget/label] :opt-un [:veil.mods.ids/overrides :widget/colors]))
(s/def ::gadget (s/keys :req-un [:veil.mods.ids/id] :opt-un [:veil.mods.ids/overrides]))

(def widget-type {:type :widget :folder "widgets" :spec ::widget :construct :label})
(def gadget-type {:type :gadget :folder "gadgets" :spec ::gadget :construct :id})
(def content-types [widget-type gadget-type])

(defn manifest
  "A mod.json path and its text."
  [id & deps]
  [(str id "/mod.json") (json/write-str {:id id :dependsOn (vec deps)})])

(defn widget
  "A widget file path and its text; extra is merged into the JSON."
  ([mod file id] (widget mod file id {}))
  ([mod file id extra]
   [(str mod "/widgets/" file ".json") (json/write-str (merge {:id id :label file} extra))]))

(defn mods
  "mods-data from [path text] pairs, with every path's top folder as a mod folder."
  [& files]
  {:folders (set (map #(first (str/split (first %) #"/")) files))
   :files   (into {} files)})
