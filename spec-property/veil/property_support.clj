(ns veil.property-support
  "Test support: run test.check properties with seed rerun capability."
  (:require [clojure.test.check :as tc]))

(def default-trials
  "The shared trial count for all property specs. No override: a soak run is a one-line edit."
  100)

(defn- env-seed
  "Read the VEIL_PROPERTY_SEED env var, parsing it to a long.
   Returns nil if unset, blank, or unparseable."
  []
  (some-> (System/getenv "VEIL_PROPERTY_SEED") parse-long))

(defn run
  "Run a property spec, returning nil if the property holds, else a message string.

   Args:
   - property: a test.check property
   - seed: optional seed for reproducibility (defaults to env-seed or nil for clock)

   Arity:
   - (run property): uses env-seed or clock if unset
   - (run property seed): uses the provided seed

   On failure, returns a message with the trial count, seed, shrunk counterexample,
   and rerun hint."
  ([property]
   (run property (env-seed)))
  ([property seed]
   (let [result (tc/quick-check default-trials property :seed seed)
         {:keys [pass? num-tests] actual-seed :seed} result]
     (when-not pass?
       (str "Property failed after " num-tests " trials.\n"
            "Seed: " actual-seed "\n"
            "Shrunk counterexample: " (get-in result [:shrunk :smallest]) "\n"
            "Rerun: VEIL_PROPERTY_SEED=" actual-seed " bb property")))))
