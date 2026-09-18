(ns veil.acceptance.runtime
  "Executes a feature's JSON IR: every scenario x example row, each step
  dispatched to a handler in veil.acceptance.steps against a fresh world."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test :refer [is testing]]
            [veil.acceptance.steps :as steps]))

(defn- examples-for [scenario]
  (let [examples (:examples scenario)]
    (if (seq examples) examples [{}])))

(defn expand-step [text example]
  (reduce-kv (fn [expanded key value]
               (str/replace expanded (str "<" (name key) ">") (str value)))
             text
             example))

(defn- execute-scenario [feature scenario index example]
  (testing (str (:name scenario) "/example_" index)
    (let [world (atom {})]
      (doseq [step (concat (:background feature []) (:steps scenario))]
        (let [result (steps/handle-step world (expand-step (:text step) example))]
          (is (:ok? result) (:message result)))))))

(defn assert-feature [ir-path]
  (let [feature (json/read-str (slurp ir-path) :key-fn keyword)]
    (doseq [scenario (:scenarios feature)
            [index example] (map-indexed vector (examples-for scenario))]
      (execute-scenario feature scenario (inc index) example))))
