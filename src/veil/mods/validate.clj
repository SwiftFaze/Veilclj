(ns veil.mods.validate
  "Turns a mod file's JSON text into data, or into field-level errors naming the
  file, the JSON path (\"/colors/BORDER/r\") and what was expected there.

  clojure.spec does the checking. Two things it doesn't do are added here:
  translating its problems into JSON paths and phrases, and rejecting keys a
  `s/keys` spec doesn't name (spec maps are open; mod files are closed)."
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(defn field-error
  "An error map for a value that isn't what its field expects:
  {:kind :invalid :file :path :expected :message}. Content types build their
  :check errors with it too, so they read the same as validation failures."
  [file path expected]
  {:kind     :invalid
   :file     file
   :path     path
   :expected expected
   :message  (str file ": " path " expected " expected)})

(defn parse
  "JSON text -> {:data map-with-keyword-keys} or {:errors [invalid-json]}."
  [file text]
  (try
    {:data (json/read-str text :key-fn keyword)}
    (catch Exception e
      {:errors [{:kind    :invalid-json
                 :file    file
                 :message (str file ": not valid JSON (" (.getMessage e) ")")}]})))

;; --- Walking spec forms ---

(defn- form-of
  "The form behind a spec: a registered keyword's definition, or the form itself."
  [spec]
  (let [form (if (keyword? spec) (some-> (s/get-spec spec) s/form) spec)]
    (if (and (keyword? form) (not= form spec)) (form-of form) form)))

(defn- form-op [form]
  (when (seq? form) (first form)))

(def ^:private structural-ops #{`s/keys `s/map-of `s/coll-of})

(defn- structural-form
  "The s/keys, s/map-of or s/coll-of form that shapes values of spec, looking
  through s/and; any other form unchanged."
  [spec]
  (let [form (form-of spec)]
    (if (= `s/and (form-op form))
      (some #(let [f (structural-form %)] (when (structural-ops (form-op f)) f))
            (rest form))
      form)))

(defn- keys-specs
  "Unqualified key -> spec for every :req-un/:opt-un entry of an s/keys form."
  [form]
  (->> (rest form)
       (partition 2)
       (filter (comp #{:req-un :opt-un} first))
       (mapcat second)
       (flatten)
       (filter keyword?)
       (map (fn [spec] [(keyword (name spec)) spec]))
       (into {})))

(defn- map-of-value-spec [form]
  (nth form 2))

(defn- coll-of-element-spec [form]
  (second form))

(defn- json-path [segments]
  (str "/" (str/join "/" (map #(if (keyword? %) (name %) (str %)) segments))))

;; --- Unknown keys ---

(declare unknown-key-segments)

(defn- unknown-in-keys [form data at]
  (let [specs (keys-specs form)]
    (concat
      (for [k (keys data) :when (not (contains? specs k))]
        (conj at k))
      (mapcat (fn [[k v]] (unknown-key-segments (specs k) v (conj at k)))
              (select-keys data (keys specs))))))

(defn- unknown-in-map-of [form data at]
  (mapcat (fn [[k v]] (unknown-key-segments (map-of-value-spec form) v (conj at k)))
          data))

(defn- unknown-in-coll-of [form data at]
  (mapcat (fn [i v] (unknown-key-segments (coll-of-element-spec form) v (conj at i)))
          (range) data))

(defn- unknown-key-segments
  "Path segments of every key in data that its s/keys spec doesn't name, at any depth."
  [spec data at]
  (let [form (structural-form spec)]
    (condp = (form-op form)
      `s/keys    (when (map? data) (unknown-in-keys form data at))
      `s/map-of  (when (map? data) (unknown-in-map-of form data at))
      `s/coll-of (when (sequential? data) (unknown-in-coll-of form data at))
      nil)))

;; --- Spec problems ---

(defn- step-into
  "Follow one :in segment down the spec form: [next-spec path-segment segments-consumed]."
  [form segments]
  (let [[segment & more] segments]
    (condp = (form-op form)
      `s/keys    [((keys-specs form) segment) segment 1]
      `s/map-of  [(if (= 0 (first more)) (second form) (map-of-value-spec form)) segment 2]
      `s/coll-of [(coll-of-element-spec form) segment 1]
      [nil segment 1])))

(defn- in->path
  "clojure.spec's :in, with the key/value index s/map-of inserts removed."
  [spec in]
  (loop [spec spec, in in, path []]
    (if (empty? in)
      path
      (let [[next-spec segment consumed] (step-into (structural-form spec) in)]
        (recur next-spec (drop consumed in) (conj path segment))))))

(defn- missing-key
  "The key named by an s/keys `(fn [%] (contains? % :k))` predicate, or nil."
  [pred]
  (when (and (seq? pred) (some #{`contains?} (flatten pred)))
    (last (filter keyword? (flatten pred)))))

(def ^:private scalar-phrases
  {"string?"  "a string"
   "int?"     "an integer"
   "integer?" "an integer"
   "boolean?" "true or false"
   "number?"  "a number"})

(defn- collection-phrase [pred]
  (let [names (set (map #(when (symbol? %) (name %)) (flatten [pred])))]
    (cond
      (names "map?")                        "an object"
      (some names ["vector?" "coll?" "sequential?"]) "a list")))

(defn- phrase
  "What a failed predicate expected, in words a modder reads."
  [problem phrases]
  (let [pred (:pred problem)
        last-via (last (:via problem))]
    (or (when (symbol? pred) (scalar-phrases (name pred)))
        (when (collection-phrase pred)
          (get phrases last-via (collection-phrase pred)))
        (get phrases last-via)
        (str "a value matching " pred))))

(defn- id-problem? [problem]
  (= 'veil.mods.ids/valid-id? (:pred problem)))

(defn- problem->error [file spec phrases problem]
  (let [segments (in->path spec (:in problem))]
    (cond
      (missing-key (:pred problem))
      (field-error file (json-path (conj segments (missing-key (:pred problem)))) "a required field")

      (id-problem? problem)
      {:kind     :malformed-id
       :file     file
       :path     (json-path segments)
       :value    (:val problem)
       :expected "an id like mod:name"
       :message  (str file ": " (json-path segments) " malformed id \"" (:val problem)
                      "\" (expected lowercase mod:name)")}

      :else
      (field-error file (json-path segments) (phrase problem phrases)))))

(defn check
  "Every problem with data against spec, as error maps (empty when valid).
  phrases maps a spec keyword to how its collection-level mismatch is worded,
  e.g. {::dependsOn \"a list of mod ids\"}."
  ([file spec data] (check file spec data {}))
  ([file spec data phrases]
   (concat
     (map (partial problem->error file spec phrases)
          (:clojure.spec.alpha/problems (s/explain-data spec data)))
     (map #(field-error file (json-path %) "no such field")
          (unknown-key-segments spec data [])))))

(defn validate
  "JSON text -> {:data map} when it parses and satisfies spec, else {:errors [...]}."
  ([file text spec] (validate file text spec {}))
  ([file text spec phrases]
   (let [{:keys [data errors]} (parse file text)
         errors (or errors (seq (check file spec data phrases)))]
     (if errors {:errors (vec errors)} {:data data}))))
