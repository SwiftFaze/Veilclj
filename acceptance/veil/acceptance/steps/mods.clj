(ns veil.acceptance.steps.mods
  "Acceptance steps for the mod loader feature. They drive the pure loader
  over in-memory mods/ data, with the fixture content types \"widget\" and
  \"gadget\" from veil.mods.fixtures, never real game content."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [veil.acceptance.step-support :refer [ok check fail]]
            [veil.mods.fixtures :as fixtures]
            [veil.mods.fonts :as fonts]
            [veil.mods.loader :as loader]
            [veil.mods.registry :as registry]
            [veil.mods.themes :as themes]))

;; --- Building the mods/ data ---

(defn- put-file! [world path text]
  (swap! world assoc-in [:files path] text)
  (ok))

(defn- put-folder! [world folder]
  (swap! world update :folders (fnil conj #{}) folder)
  (ok))

(defn- put-manifest! [world id & deps]
  (apply put-file! world (apply fixtures/manifest id deps)))

(defn- file-name [id]
  (str/replace id ":" "_"))

(defn- content-file
  "[path text] for a fixture widget or gadget declared by a step."
  [mod kind id label overrides]
  (let [extra (cond-> {}
                label     (assoc :label label)
                overrides (assoc :overrides overrides))]
    (case kind
      "widget" (fixtures/widget mod (file-name id) id extra)
      "gadget" [(str mod "/gadgets/" (file-name id) ".json")
                (json/write-str (merge {:id id} extra))])))

(defn- declare-content! [world mod kind id label overrides]
  (let [[path text] (content-file mod kind id label overrides)]
    (swap! world update :declared (fnil conj [])
           {:mod mod :id id :overrides overrides :file path})
    (put-file! world path text)))

(defn- declared-file
  "The file of the first content step that matches criteria, e.g. {:id \"core:lever\"}."
  [world criteria]
  (->> (:declared @world)
       (filter #(= criteria (select-keys % (keys criteria))))
       first
       :file))

(defn- load-mods! [world]
  (let [{:keys [files folders]} @world
        top-folder (comp first #(str/split % #"/"))
        mods-data {:folders (into (set folders) (map top-folder (keys files)))
                   :files   (or files {})}
        content-types (into fixtures/content-types [themes/content-type fonts/content-type])]
    (swap! world assoc :result (loader/load-mods mods-data content-types))
    (ok)))

;; --- Asserting on the result ---

(defn- with-registry [world f]
  (let [{:keys [registry errors]} (:result @world)]
    (if registry
      (f registry)
      (fail (str "loading failed:\n" (loader/error-report errors))))))

(defn- errors-include [world expected? described]
  (let [{:keys [registry errors]} (:result @world)]
    (if registry
      (fail (str "loading succeeded, expected an error with " described))
      (check (some expected? errors)
             (str "no error with " described " in:\n" (loader/error-report errors))))))

(defn- matching
  "A predicate: does an error carry every key/value of expected?"
  [expected]
  #(= expected (select-keys % (keys expected))))

(defn- fails-with [world expected]
  (errors-include world (matching expected) (pr-str expected)))

(defn- parse-order [text]
  (if (= "empty" text) [] (str/split text #", ")))

(defn- entry-of [registry kind id]
  (registry/entry registry (keyword kind) id))

(def handlers
  [[#"there is no mods directory"
    (fn [_ _] (ok))]

   [#"mod \"([^\"]+)\" has no dependencies"
    (fn [world [_ id]] (put-manifest! world id))]

   [#"mod \"([^\"]+)\" depends on \"([^\"]+)\""
    (fn [world [_ id dep]] (put-manifest! world id dep))]

   [#"mod \"[^\"]+\" has a manifest \"([^\"]+)\" containing (.+)"
    (fn [world [_ path text]] (put-file! world path text))]

   [#"mod \"[^\"]+\" has a widget file \"([^\"]+)\" containing (.+)"
    (fn [world [_ path text]] (put-file! world path text))]

   [#"mod \"[^\"]+\" has a file \"([^\"]+)\" containing (.+)"
    (fn [world [_ path text]] (put-file! world path text))]

   [#"mod \"([^\"]+)\" has a (widget|gadget) \"([^\"]+)\"(?: labelled \"([^\"]+)\")?(?: that overrides \"([^\"]+)\")?"
    (fn [world [_ mod kind id label overrides]]
      (declare-content! world mod kind id label overrides))]

   [#"the mods directory has a folder \"([^\"]+)\" with no mod.json"
    (fn [world [_ folder]] (put-folder! world folder))]

   [#"the mods directory has a folder \"([^\"]+)\" whose manifest id is \"([^\"]+)\""
    (fn [world [_ folder id]]
      (put-file! world (str folder "/mod.json") (json/write-str {:id id :dependsOn []})))]

   [#"the mods are loaded"
    (fn [world _] (load-mods! world))]

   [#"the load order is (.+)"
    (fn [world [_ text]]
      (with-registry world
        #(check (= (parse-order text) (registry/load-order %))
                (str "load order is " (registry/load-order %)))))]

   [#"no content of any type is registered"
    (fn [world _]
      (with-registry world
        #(check (empty? (:content %)) (str "registered content: " (:content %)))))]

   [#"(widget|gadget) \"([^\"]+)\" is registered from mod \"([^\"]+)\""
    (fn [world [_ kind id mod]]
      (with-registry world
        #(check (= mod (:mod (entry-of % kind id)))
                (str kind " " id " is registered as " (entry-of % kind id)))))]

   [#"(widget|gadget) \"([^\"]+)\" is labelled \"([^\"]+)\""
    (fn [world [_ kind id label]]
      (with-registry world
        #(check (= label (:value (entry-of % kind id)))
                (str kind " " id " is registered as " (entry-of % kind id)))))]

   [#"no (widget|gadget) is registered as \"([^\"]+)\""
    (fn [world [_ kind id]]
      (with-registry world
        #(check (nil? (entry-of % kind id))
                (str kind " " id " is registered as " (entry-of % kind id)))))]

   [#"loading fails naming mod \"([^\"]+)\" and the unresolved dependency \"([^\"]+)\""
    (fn [world [_ mod dep]]
      (fails-with world {:kind :unresolved-dependency :mod mod :dependency dep}))]

   [#"loading fails naming the cyclic mods \"([^\"]+)\" and \"([^\"]+)\""
    (fn [world [_ a b]]
      (fails-with world {:kind :dependency-cycle :mods (vec (sort [a b]))}))]

   [#"loading fails naming the folder \"([^\"]+)\" as missing its mod.json"
    (fn [world [_ folder]]
      (fails-with world {:kind :missing-manifest :folder folder}))]

   [#"loading fails naming the file \"([^\"]+)\", the id \"([^\"]+)\" and the folder name \"([^\"]+)\""
    (fn [world [_ file id folder]]
      (fails-with world {:kind :folder-mismatch :file file :id id :folder folder}))]

   [#"loading fails naming the widget file and the malformed id \"([^\"]+)\""
    (fn [world [_ id]]
      (fails-with world {:kind :malformed-id :file (declared-file world {:id id}) :value id}))]

   [#"loading fails naming the widget file, the id \"([^\"]+)\" and that mod \"([^\"]+)\" may only add IDs in namespace \"([^\"]+)\""
    (fn [world [_ id mod namespace]]
      (let [expected {:kind :foreign-namespace :id id :mod mod
                      :file (declared-file world {:id id :mod mod})}]
        (errors-include world
                        (every-pred (matching expected)
                                    #(str/includes? (:message %) (str "namespace \"" namespace "\"")))
                        (str (pr-str expected) " in namespace " namespace))))]

   [#"loading fails naming the colliding id \"([^\"]+)\" and the mods \"([^\"]+)\" and \"([^\"]+)\""
    (fn [world [_ id a b]]
      (fails-with world {:kind :collision :id id :mods [a b]}))]

   [#"loading fails naming the colliding id \"([^\"]+)\" and the files \"([^\"]+)\" and \"([^\"]+)\""
    (fn [world [_ id a b]]
      (fails-with world {:kind :duplicate-in-mod :id id :files (vec (sort [a b]))}))]

   [#"loading fails naming the widget file, the id \"([^\"]+)\" and the mismatched overrides \"([^\"]+)\""
    (fn [world [_ id overrides]]
      (fails-with world {:kind      :overrides-mismatch
                         :file      (declared-file world {:id id :overrides overrides})
                         :id        id
                         :overrides overrides}))]

   [#"loading fails naming the widget file and that there is no \"([^\"]+)\" to override"
    (fn [world [_ id]]
      (fails-with world {:kind :nothing-to-override
                         :file (declared-file world {:id id :overrides id})
                         :id   id}))]

   [#"loading fails naming mod \"([^\"]+)\", the id \"([^\"]+)\" and that it must declare \"dependsOn\" of \"([^\"]+)\""
    (fn [world [_ mod id target]]
      (fails-with world {:kind :override-without-dependency :mod mod :id id :target-mod target}))]

   [#"the load errors include the file \"([^\"]+)\", the path \"([^\"]+)\" and that it expected (.+)"
    (fn [world [_ file path expected]]
      (errors-include world
                      (matching {:file file :path path :expected expected})
                      (str file " " path " expected " expected)))]

   [#"the load errors include the file \"([^\"]+)\" as not valid JSON"
    (fn [world [_ file]]
      (fails-with world {:kind :invalid-json :file file}))]])
