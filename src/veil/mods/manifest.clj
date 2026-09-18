(ns veil.mods.manifest
  "mods/<id>/mod.json: which folders are mods, their ids and dependencies."
  (:require [clojure.spec.alpha :as s]
            [veil.mods.validate :as validate]))

(s/def ::id string?)
(s/def ::dependsOn (s/coll-of string? :kind vector?))
(s/def ::manifest (s/keys :req-un [::id] :opt-un [::dependsOn]))

(def ^:private phrases {::dependsOn "a list of mod ids"})

(defn manifest-path [folder]
  (str folder "/mod.json"))

(defn- missing-manifest [folder]
  {:kind    :missing-manifest
   :folder  folder
   :message (str "mods/" folder " has no mod.json")})

(defn- folder-mismatch [file id folder]
  {:kind    :folder-mismatch
   :file    file
   :id      id
   :folder  folder
   :message (str file ": id \"" id "\" must match its folder name \"" folder "\"")})

(defn- parse-manifest
  "{:manifest m} or {:errors [...]} for one mod.json's text."
  [file text folder]
  (let [{:keys [data errors]} (validate/validate file text ::manifest phrases)]
    (cond
      errors                   {:errors errors}
      (not= folder (:id data)) {:errors [(folder-mismatch file (:id data) folder)]}
      :else {:manifest {:id (:id data) :depends-on (:dependsOn data []) :file file}})))

(defn- read-manifest
  [files folder]
  (let [file (manifest-path folder)]
    (if-let [text (files file)]
      (parse-manifest file text folder)
      {:errors [(missing-manifest folder)]})))

(defn read-manifests
  "Every mod folder's manifest, plus every problem found across all of them:
  {:manifests [{:id :depends-on :file}] :errors [...]}."
  [{:keys [folders files]}]
  (let [results (map (partial read-manifest files) (sort folders))]
    {:manifests (vec (keep :manifest results))
     :errors    (vec (mapcat :errors results))}))
