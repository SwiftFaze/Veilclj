(ns veil.mods.loader
  "Builds the mod registry from already-read mods/ data (veil.mods.disk).

  A content type is {:type kw :folder \"widgets\" :spec spec :construct fn}:
  every mod's <folder>/*.json is validated against :spec and its data turned
  into the registered value by :construct. Loading is three passes, each
  needing the one before to have succeeded:
    1. validate every manifest and content file, collecting every problem;
    2. order the mods by dependency (first problem only);
    3. register content in load order (first problem only)."
  (:require [clojure.string :as str]
            [veil.mods.ids :as ids]
            [veil.mods.manifest :as manifest]
            [veil.mods.order :as order]
            [veil.mods.registry :as registry]
            [veil.mods.validate :as validate]))

;; --- Pass 1: validation ---

(defn- claimed-type
  "The content type that claims path \"<mod>/<folder>/<name>.json\", or nil.
  Folders no type claims and non-JSON files are deliberately ignored."
  [types-by-folder mod-ids path]
  (let [[mod folder file & more] (str/split path #"/")]
    (when (and file (nil? more) (mod-ids mod) (str/ends-with? file ".json"))
      (types-by-folder folder))))

(defn- content-files [files content-types manifests]
  (let [types-by-folder (into {} (map (juxt :folder identity) content-types))
        mod-ids (set (map :id manifests))]
    (for [[path text] (sort-by key files)
          :let [content-type (claimed-type types-by-folder mod-ids path)]
          :when content-type]
      {:mod (first (str/split path #"/")) :content-type content-type :file path :text text})))

(defn- overrides-mismatch [file id overrides]
  {:kind      :overrides-mismatch
   :file      file
   :id        id
   :overrides overrides
   :message   (str file ": \"overrides\" is \"" overrides "\" but must equal the file's id \""
                   id "\"")})

(defn- read-entry
  "{:entry e} or {:errors [...]} for one content file."
  [{:keys [mod content-type file text]}]
  (let [{:keys [data errors]} (validate/validate file text (:spec content-type))
        {:keys [id overrides]} data]
    (cond
      errors                            {:errors errors}
      (and overrides (not= overrides id)) {:errors [(overrides-mismatch file id overrides)]}
      :else {:entry {:mod       mod
                     :type      (:type content-type)
                     :file      file
                     :id        id
                     :overrides overrides
                     :value     ((:construct content-type) data)}})))

(defn- duplicate-in-mod [[_mod _type id] entries]
  (let [files (sort (map :file entries))]
    {:kind    :duplicate-in-mod
     :id      id
     :files   (vec files)
     :message (str "id \"" id "\" is declared by more than one file: " (str/join ", " files))}))

(defn- duplicates-in-mods [entries]
  (for [[k group] (group-by (juxt :mod :type :id) entries)
        :when (< 1 (count group))]
    (duplicate-in-mod k group)))

;; --- Pass 3: registration ---

(defn- collision [{:keys [id mod owner]}]
  {:kind    :collision
   :id      id
   :mods    [owner mod]
   :message (str "id \"" id "\" from mod \"" mod "\" collides with the one from mod \"" owner
                 "\"; add \"overrides\": \"" id "\" to replace it")})

(defn- override-without-dependency [{:keys [id mod target]}]
  {:kind       :override-without-dependency
   :mod        mod
   :id         id
   :target-mod target
   :message    (str "mod \"" mod "\" overrides \"" id "\" but does not depend on \"" target
                    "\"; add \"dependsOn\": [\"" target "\"] to its mod.json")})

(defn- nothing-to-override [{:keys [id file]}]
  {:kind    :nothing-to-override
   :file    file
   :id      id
   :message (str file ": there is no \"" id "\" to override")})

(defn- foreign-namespace [{:keys [id file mod]}]
  {:kind    :foreign-namespace
   :file    file
   :id      id
   :mod     mod
   :message (str file ": mod \"" mod "\" may only add IDs in namespace \"" mod "\", not \"" id "\"")})

(defn- replacing-error
  "Why an entry can't take an ID another mod already registered, or nil."
  [{:keys [overrides reachable?] :as claim}]
  (cond
    (not overrides)  (collision claim)
    (not reachable?) (override-without-dependency claim)))

(defn- adding-error
  "Why an entry can't register an ID nobody has registered yet, or nil."
  [{:keys [overrides reachable? target mod] :as claim}]
  (cond
    (and overrides (not reachable?)) (override-without-dependency claim)
    overrides                        (nothing-to-override claim)
    (not= target mod)                (foreign-namespace claim)))

(defn- claim
  "An entry, plus who owns its ID so far and whether the ID's mod is itself or a dependency."
  [registry manifests {:keys [mod type id] :as entry}]
  (let [target (ids/namespace-of id)]
    (assoc entry
      :owner (:mod (registry/entry registry type id))
      :target target
      :reachable? (or (= target mod) (contains? (order/transitive-deps manifests mod) target)))))

(defn- register-entry [registry manifests entry]
  (let [{:keys [owner] :as c} (claim registry manifests entry)]
    (if-let [error (if owner (replacing-error c) (adding-error c))]
      (reduced {:errors [error]})
      (registry/register-entry registry (:type entry) (:id entry) (:mod entry) (:file entry)
                               (:value entry)))))

(defn- registration-order [load-order content-types]
  (let [mod-rank (zipmap load-order (range))
        type-rank (zipmap (map :type content-types) (range))]
    (juxt (comp mod-rank :mod) (comp type-rank :type) :file)))

(defn- register-all [load-order content-types manifests entries]
  (let [result (reduce #(register-entry %1 manifests %2)
                       (registry/with-load-order (registry/empty-registry) load-order)
                       (sort-by (registration-order load-order content-types) entries))]
    (if (:errors result) result {:registry result})))

;; --- Entry points ---

(defn load-mods
  "{:registry r} for mods-data ({:folders #{..} :files {path text}}), or
  {:errors [...]} describing why it can't be loaded."
  [mods-data content-types]
  (let [{:keys [manifests errors]} (manifest/read-manifests mods-data)
        results (map read-entry (content-files (:files mods-data) content-types manifests))
        entries (keep :entry results)
        errors (concat errors (mapcat :errors results) (duplicates-in-mods entries))
        {:keys [load-order error]} (when (empty? errors) (order/order manifests))]
    (cond
      (seq errors) {:errors (vec errors)}
      error        {:errors [error]}
      :else        (register-all load-order content-types manifests entries))))

(defn error-report
  "The text shown when mods fail to load: a heading, then one problem per line."
  [errors]
  (str/join "\n" (cons "Failed to load mods:" (map #(str "  " (:message %)) errors))))

(defn startup
  "Load mods and return {:registry r} on success or {:error msg :exit-status 1} on failure.
  Pure function - no I/O."
  [mods-data content-types]
  (let [result (load-mods mods-data content-types)]
    (if (contains? result :errors)
      {:error (error-report (:errors result)) :exit-status 1}
      {:registry (:registry result)})))
