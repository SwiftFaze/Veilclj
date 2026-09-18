(ns veil.acceptance.generator
  "APS acceptance entrypoint generator: one clojure.test namespace per feature
  IR, plus the metadata file the APS mutator reads. The generated test runs
  whatever IR path it is given, so mutated IRs reuse the same entry points."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.security MessageDigest]))

(defn slug [value]
  (let [slugged (-> value
                    str/lower-case
                    (str/replace #"[^a-z0-9]+" "-")
                    (str/replace #"(^-|-$)" ""))]
    (if (seq slugged) slugged "feature")))

(defn- sha256 [text]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes text "UTF-8"))]
    (str "sha256:" (apply str (map #(format "%02x" (bit-and % 0xff)) digest)))))

(defn generated-ns [feature-name]
  (str "veil.acceptance.generated." (slug feature-name) "-test"))

(defn- generated-file [output-dir feature-name]
  (io/file output-dir "veil" "acceptance" "generated"
           (str (str/replace (slug feature-name) "-" "_") "_test.clj")))

(defn generated-source [feature-name ir-path]
  (format "(ns %s
  (:require [clojure.test :refer [deftest]]
            [veil.acceptance.runtime :as runtime]))

(deftest generated-acceptance
  (runtime/assert-feature %s))
"
          (generated-ns feature-name)
          (pr-str ir-path)))

(defn- metadata [feature ir-path generated-path source]
  {:schema_version      1
   :feature_path        (or (get-in feature [:metadata :feature_path]) "unknown")
   :ir_path             ir-path
   :implementation_hash (sha256 source)
   :hash_scope          "generated_files"
   :generated_files     [generated-path]})

(defn generate!
  "Writes the entry point and metadata for one IR file; returns the entry
  point's {:path :ns}."
  [ir-path output-dir]
  (let [feature (json/read-str (slurp ir-path) :key-fn keyword)
        file    (generated-file output-dir (:name feature))
        path    (str/replace (.getPath file) "\\" "/")
        source  (generated-source (:name feature) ir-path)
        meta    (io/file output-dir "metadata" (str (slug ir-path) ".json"))]
    (io/make-parents file)
    (io/make-parents meta)
    (spit file source)
    (spit meta (json/write-str (metadata feature ir-path path source) :escape-slash false))
    {:path path :ns (symbol (generated-ns (:name feature)))}))
