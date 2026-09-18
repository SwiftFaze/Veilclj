(ns build
  (:require [clojure.string :as str]
            [clojure.tools.build.api :as b]))

;; version.txt is owned by Release Please - never hand-edit it (docs/release.md).
(def version (str/trim (slurp "version.txt")))
(def class-dir "target/classes")
(def uber-file (format "target/veil-%s.jar" version))
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src"] :target-dir class-dir})
  (b/compile-clj {:basis @basis :ns-compile '[veil.main] :class-dir class-dir})
  (b/uber {:class-dir class-dir :uber-file uber-file :basis @basis :main 'veil.main})
  (println "Built" uber-file))
