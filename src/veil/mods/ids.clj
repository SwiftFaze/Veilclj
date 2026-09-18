(ns veil.mods.ids
  "Namespaced content IDs: \"<mod>:<name>\", both halves lowercase."
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(def id-pattern
  #"^[a-z0-9][a-z0-9_-]*:[a-z0-9][a-z0-9_-]*$")

(defn valid-id?
  [s]
  (boolean (re-matches id-pattern s)))

(defn namespace-of
  "The mod half of an ID: \"core\" for \"core:lever\"."
  [id]
  (first (str/split id #":")))

(s/def ::id (s/and string? valid-id?))
(s/def ::overrides string?)
