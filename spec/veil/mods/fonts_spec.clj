(ns veil.mods.fonts-spec
  (:require [speclj.core :refer :all]
    [veil.mods.fonts :as fonts]
    [veil.mods.loader :as loader]
    [veil.mods.fixtures :as f]
    [veil.mods.validate :as validate]))

(defn- font-errors
  "The validation errors for a font descriptor's JSON text."
  [json-text]
  (:errors (validate/validate "f.json" json-text (:spec fonts/content-type) (:phrases fonts/content-type))))

(describe "font-file-name?"
  (it "accepts .ttf files with no path separators"
    (should (fonts/font-file-name? "Mono.ttf"))
    (should (fonts/font-file-name? "JetBrainsMono-Regular.ttf")))

  (it "accepts .otf files with no path separators"
    (should (fonts/font-file-name? "Mono.otf"))
    (should (fonts/font-file-name? "DejaVuSans.otf")))

  (it "rejects files with forward slashes"
    (should-not (fonts/font-file-name? "sub/Mono.ttf"))
    (should-not (fonts/font-file-name? "../Mono.ttf")))

  (it "rejects files with backslashes"
    (should-not (fonts/font-file-name? "sub\\Mono.ttf")))

  (it "rejects files starting with /"
    (should-not (fonts/font-file-name? "/Mono.ttf")))

  (it "rejects files with wrong extensions"
    (should-not (fonts/font-file-name? "Mono.woff"))
    (should-not (fonts/font-file-name? "Mono"))
    (should-not (fonts/font-file-name? "Mono.ttf.txt"))))

(describe "spec validation"
  (it "accepts a valid font"
    (let [result (validate/validate "f.json" "{\"id\": \"core:default\", \"file\": \"Mono.ttf\", \"size\": 20}" (:spec fonts/content-type))]
      (should (contains? result :data))
      (should-not (contains? result :errors))))

  (it "rejects size 0"
    (let [errors (font-errors "{\"id\": \"core:default\", \"file\": \"Mono.ttf\", \"size\": 0}")]
      (should= #{["/size" "a positive integer"]}
               (set (map (juxt :path :expected) errors)))))

  (it "rejects negative size"
    (let [errors (font-errors "{\"id\": \"core:default\", \"file\": \"Mono.ttf\", \"size\": -5}")]
      (should= #{["/size" "a positive integer"]}
               (set (map (juxt :path :expected) errors)))))

  (it "rejects non-integer size"
    (let [errors (font-errors "{\"id\": \"core:default\", \"file\": \"Mono.ttf\", \"size\": 12.5}")]
      (should= #{["/size" "a positive integer"]}
               (set (map (juxt :path :expected) errors)))))

  (it "rejects string size"
    (let [errors (font-errors "{\"id\": \"core:default\", \"file\": \"Mono.ttf\", \"size\": \"20\"}")]
      (should= #{["/size" "a positive integer"]}
               (set (map (juxt :path :expected) errors)))))

  (it "rejects path-like file names"
    (let [errors (font-errors "{\"id\": \"core:default\", \"file\": \"sub/Mono.ttf\", \"size\": 20}")]
      (should= #{["/file" "a font file name ending in .ttf or .otf"]}
               (set (map (juxt :path :expected) errors)))))

  (it "rejects missing id"
    (let [errors (font-errors "{\"file\": \"Mono.ttf\", \"size\": 20}")]
      (should= #{["/id" "a required field"]}
               (set (map (juxt :path :expected) errors)))))

  (it "rejects missing file"
    (let [errors (font-errors "{\"id\": \"core:default\", \"size\": 20}")]
      (should= #{["/file" "a required field"]}
               (set (map (juxt :path :expected) errors)))))

  (it "rejects missing size"
    (let [errors (font-errors "{\"id\": \"core:default\", \"file\": \"Mono.ttf\"}")]
      (should= #{["/size" "a required field"]}
               (set (map (juxt :path :expected) errors)))))

  (it "rejects unknown fields"
    (let [errors (font-errors "{\"id\": \"core:default\", \"file\": \"Mono.ttf\", \"size\": 20, \"family\": \"Mono\"}")]
      (should= #{["/family" "no such field"]}
               (set (map (juxt :path :expected) errors))))))

(describe "construct"
  (it "returns file and size from the descriptor"
    (let [result (fonts/construct {:id "core:default" :file "Mono.ttf" :size 20})]
      (should= {:file "Mono.ttf" :size 20} result)))

  (it "includes overrides when present"
    (let [result (fonts/construct {:id "core:other" :file "Wide.otf" :size 16 :overrides "core:default"})]
      (should= {:file "Wide.otf" :size 16} result))))

(describe "font"
  (it "returns font with full path and size when registered"
    (let [mods-data (f/mods (f/manifest "core")
                            ["core/fonts/default.json" "{\"id\": \"core:default\", \"file\": \"Mono.ttf\", \"size\": 20}"]
                            ["core/fonts/Mono.ttf" "font bytes"])
          load-result (loader/load-mods mods-data [fonts/content-type])
          registry (:registry load-result)
          result (fonts/font registry "core:default")]
      (should= {:file "core/fonts/Mono.ttf" :size 20} result)))

  (it "returns nil when id is not registered"
    (let [mods-data (f/mods (f/manifest "core"))
          load-result (loader/load-mods mods-data [fonts/content-type])
          registry (:registry load-result)
          result (fonts/font registry "core:default")]
      (should-be-nil result))))

(describe "startup"
  (it "returns font when default-id is registered"
    (let [mods-data (f/mods (f/manifest "core")
                            ["core/fonts/default.json" "{\"id\": \"core:default\", \"file\": \"Mono.ttf\", \"size\": 20}"]
                            ["core/fonts/Mono.ttf" "font bytes"])
          load-result (loader/load-mods mods-data [fonts/content-type])
          registry (:registry load-result)
          result (fonts/startup registry fonts/default-id)]
      (should (contains? result :font))
      (should-not (contains? result :error))))

  (it "returns error when default-id is not registered"
    (let [mods-data (f/mods (f/manifest "core"))
          load-result (loader/load-mods mods-data [fonts/content-type])
          registry (:registry load-result)
          result (fonts/startup registry fonts/default-id)]
      (should (contains? result :error))
      (should= 1 (:exit-status result))
      (should (.contains (:error result) "core:default"))))

  (it "error message uses error-report format"
    (let [mods-data (f/mods (f/manifest "core"))
          load-result (loader/load-mods mods-data [fonts/content-type])
          registry (:registry load-result)
          result (fonts/startup registry fonts/default-id)]
      (should (.startsWith (:error result) "Failed to load mods:")))))
