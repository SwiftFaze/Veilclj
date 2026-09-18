(ns veil.mods.order
  "Load order: core first, then any mod whose dependencies have all loaded,
  alphabetically by id among mods ready at the same time."
  (:require [clojure.string :as str]))

(defn- by-id [manifests]
  (into {} (map (juxt :id identity) manifests)))

(defn transitive-deps
  "Every mod id reachable through mod-id's dependsOn, excluding mod-id itself."
  [manifests mod-id]
  (let [deps (comp :depends-on (by-id manifests))]
    (loop [seen #{}, todo (vec (deps mod-id))]
      (if-let [[id & more] (seq todo)]
        (if (seen id)
          (recur seen (vec more))
          (recur (conj seen id) (into (vec more) (deps id))))
        (disj seen mod-id)))))

(defn- unresolved [manifests]
  (let [present (set (map :id manifests))]
    (first (for [{:keys [id depends-on]} manifests
                 dep depends-on
                 :when (not (present dep))]
             {:kind       :unresolved-dependency
              :mod        id
              :dependency dep
              :message    (str "mod \"" id "\" depends on \"" dep "\", which is not in mods/")}))))

(defn- cycle-error [remaining]
  (let [ids (sort remaining)]
    {:kind    :dependency-cycle
     :mods    (vec ids)
     :message (str "dependency cycle among mods: " (str/join ", " ids))}))

(defn- next-mod
  "The mod to load next from those whose dependencies have all loaded, or nil."
  [loaded remaining]
  (let [ready (filter #(every? (set loaded) (:depends-on %)) remaining)
        ids (set (map :id ready))]
    (if (ids "core") "core" (first (sort ids)))))

(defn order
  "{:load-order [ids]} or {:error e} for the first unresolved dependency or cycle."
  [manifests]
  (if-let [error (unresolved manifests)]
    {:error error}
    (loop [loaded [], remaining (set manifests)]
      (if (empty? remaining)
        {:load-order loaded}
        (if-let [id (next-mod loaded remaining)]
          (recur (conj loaded id) (set (remove #(= id (:id %)) remaining)))
          {:error (cycle-error (map :id remaining))})))))
