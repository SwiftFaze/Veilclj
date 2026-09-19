(ns veil.acceptance.steps.fonts
  "Acceptance steps for the fonts feature."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [veil.acceptance.step-support :refer [ok check fail]]
            [veil.mods.fonts :as fonts]
            [veil.mods.fixtures :as fixtures]
            [veil.mods.loader :as loader]
            [veil.mods.registry :as registry]
            [veil.mods.themes :as themes])
  (:import [java.awt Font]))

(defn- parse-json-value
  "Parse a JSON literal that might be a number, string, or other value."
  [text]
  (try
    (json/read-str text)
    (catch Exception _
      text)))

(defn- load-world
  "Load the mods the scenario's files describe, with themes and fonts as content types."
  [world]
  (let [files (:files @world)]
    (loader/load-mods {:folders (set (map #(first (str/split % #"/")) (keys files)))
                       :files files}
                      (into fixtures/content-types [themes/content-type fonts/content-type]))))

(defn- loading-failed [load-result]
  (fail (str "loading failed:\n" (loader/error-report (:errors load-result)))))

(defn- with-registered-font
  "Run (f entry full-file) for font id in the loaded world, where full-file is
  the entry's path from the mods directory; fail when loading failed."
  [world id f]
  (let [load-result (load-world world)]
    (if-let [reg (:registry load-result)]
      (let [entry (registry/entry reg :font id)]
        (f entry (when entry (str (:mod entry) "/fonts/" (-> entry :value :file)))))
      (loading-failed load-result))))

(defn- start-outcome
  "What the font startup step gives for the loaded world: {:font f} or {:error msg}."
  [world]
  (try
    (let [load-result (load-world world)]
      (if-let [reg (:registry load-result)]
        (fonts/startup reg fonts/default-id)
        {:error (loader/error-report (:errors load-result))}))
    (catch Exception e
      {:error (str "Exception: " (.getMessage e))})))

(def handlers
  [[#"mod \"([^\"]+)\" has a font \"([^\"]+)\" using file \"([^\"]+)\" at size (.+?)(?:\s+that overrides \"([^\"]+)\")?"
    (fn [world [_ mod id file size-str overrides]]
      (let [font-name (second (str/split id #":"))
            size (parse-json-value size-str)
            font-data (cond-> {:id id :file file :size size}
                        overrides (assoc :overrides overrides))
            path (str mod "/fonts/" font-name ".json")]
        (swap! world assoc-in [:files path] (json/write-str font-data))
        (ok)))]

   [#"mod \"([^\"]+)\" has the font file \"([^\"]+)\""
    (fn [world [_ mod path]]
      (swap! world assoc-in [:files path] "font bytes")
      (ok))]

   [#"mod \"([^\"]+)\" has the font descriptor \"([^\"]+)\" containing (.+)"
    (fn [world [_ mod path content]]
      (swap! world assoc-in [:files path] content)
      (ok))]

   [#"mod \"([^\"]+)\" has the shipped font \"([^\"]+)\""
    (fn [world [_ mod path]]
      (let [shipped-path (str "mods/" path)
            content (slurp shipped-path)
            descriptor (json/read-str content :key-fn keyword)
            font-file (:file descriptor)
            font-dir (str/join "/" (butlast (str/split path #"/")))
            font-path (str font-dir "/" font-file)]
        (if (.exists (java.io.File. (str "mods/" font-path)))
          (do
            (swap! world assoc-in [:files path] content)
            (swap! world assoc-in [:files font-path] "font bytes")
            (ok))
          (fail (str "shipped font file mods/" font-path " does not exist")))))]

   [#"mod \"([^\"]+)\" ships no fonts"
    (fn [world [_ mod]]
      (ok))]

   [#"font \"([^\"]+)\" is registered from mod \"([^\"]+)\" with file \"([^\"]+)\" and size (\d+)"
    (fn [world [_ id mod file size]]
      (with-registered-font world id
        (fn [entry full-file]
          (check (and entry
                      (= mod (:mod entry))
                      (= file full-file)
                      (= (Integer/parseInt size) (-> entry :value :size)))
                 (str "font " id " is registered from mod " mod " with file " file " and size " size)))))]

   [#"font \"([^\"]+)\" is registered from mod \"([^\"]+)\" with file \"([^\"]+)\""
    (fn [world [_ id mod file]]
      (with-registered-font world id
        (fn [entry full-file]
          (check (and entry
                      (= mod (:mod entry))
                      (= file full-file))
                 (str "font " id " is registered from mod " mod " with file " file)))))]

   [#"the font for startup is chosen from the loaded mods"
    (fn [world _]
      (let [outcome (start-outcome world)]
        (if-let [error (:error outcome)]
          (swap! world #(-> % (assoc :startup-error error) (dissoc :startup-font)))
          (swap! world #(-> % (assoc :startup-font (:font outcome)) (dissoc :startup-error)))))
      (ok))]

   [#"the startup font is the file \"([^\"]+)\" at size (\d+)"
    (fn [world [_ file size]]
      (let [startup-font (:startup-font @world)
            expected-size (Integer/parseInt size)]
        (check (and startup-font
                    (= file (:file startup-font))
                    (= expected-size (:size startup-font)))
               (str "startup font is " file " at size " size))))]

   [#"starting fails naming the missing font \"([^\"]+)\""
    (fn [world [_ id]]
      (let [error (:startup-error @world)]
        (check (and error (.contains error id))
               (str "starting fails naming the missing font " id))))]

   [#"the shipped font file \"([^\"]+)\" has a glyph for every code point from U\+([0-9A-Fa-f]+) to U\+([0-9A-Fa-f]+)"
    (fn [world [_ path start-hex end-hex]]
      (let [shipped-path (str "mods/" path)
            start-code (Integer/parseInt start-hex 16)
            end-code (Integer/parseInt end-hex 16)]
        (try
          (let [f (java.io.File. shipped-path)
                font (Font/createFont Font/TRUETYPE_FONT f)
                missing (vec (filter #(not (.canDisplay font %))
                                     (range start-code (inc end-code))))]
            (if (empty? missing)
              (ok)
              (let [missing-names (map #(format "U+%04X" %) (take 5 missing))]
                (fail (str path " missing glyphs: " (str/join ", " missing-names))))))
          (catch Exception e
            (fail (str "Could not check " path ": " (.getMessage e)))))))]

   [#"the shipped file \"([^\"]+)\" exists"
    (fn [world [_ path]]
      (let [shipped-path (str "mods/" path)
            f (java.io.File. shipped-path)]
        (check (.exists f)
               (str "shipped file " path " exists"))))]

   [#"the shipped file \"([^\"]+)\" contains \"([^\"]+)\""
    (fn [world [_ path text]]
      (let [shipped-path (str "mods/" path)
            f (java.io.File. shipped-path)]
        (if (.exists f)
          (let [content (slurp shipped-path)]
            (check (.contains content text)
                   (str "shipped file " path " contains \"" text "\"")))
          (fail (str "shipped file " path " does not exist")))))]

   [#"the shipped folder \"([^\"]+)\" holds exactly (\d+) font file"
    (fn [world [_ folder count-str]]
      (let [expected-count (Integer/parseInt count-str)
            shipped-folder (str "mods/" folder)
            dir (java.io.File. shipped-folder)
            font-files (if (.exists dir)
                         (vec (filter #(or (.endsWith % ".ttf") (.endsWith % ".otf"))
                                      (map #(.getName %) (.listFiles dir))))
                         [])]
        (check (= expected-count (count font-files))
               (str "shipped folder " folder " holds " (count font-files) " font files"))))]])
