(ns veil-tools.crap-gate
  "Fails when any function's CRAP score exceeds quality-gates.edn :crap-max.
  crap4clj itself only reports; this turns its .metrics/crap.edn into a gate."
  (:require [clojure.edn :as edn]))

(def metrics-file ".metrics/crap.edn")

(defn offenders [entries crap-max]
  (->> entries
       (filter #(some-> (:crap %) (> crap-max)))
       (sort-by :crap >)))

(defn- describe [{:keys [namespace name complexity coverage crap]}]
  (format "    %s/%s  CRAP %.1f  (CC %d, coverage %.0f%%)"
          namespace name (double crap) complexity (double (or coverage 0))))

(defn check
  "Returns an exit code: 0 pass, 1 offenders found."
  []
  (let [crap-max (:crap-max (edn/read-string (slurp "quality-gates.edn")))
        found    (offenders (:entries (edn/read-string (slurp metrics-file))) crap-max)]
    (if (empty? found)
      (do (println (str "  PASS  every function has CRAP <= " crap-max)) 0)
      (do (println (str "  FAIL  " (count found) " function(s) over CRAP " crap-max ":"))
          (run! (comp println describe) found)
          (println "      -> Cover it with specs, or split it - CRAP = CC^2 x (1-cov)^3 + CC.")
          1))))
