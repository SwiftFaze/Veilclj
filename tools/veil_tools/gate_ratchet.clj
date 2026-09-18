(ns veil-tools.gate-ratchet
  "Makes weakening a quality gate loud. Compares the gate config on HEAD with
  the base branch and fails on any change in the weaker direction:
    - quality-gates.edn :crap-max raised or removed
    - dependency-checker.edn: a component allowed a new dependency, a
      namespace exception or ignored component added, or a :fail-on-* turned off
  Strengthening always passes. A justified weakening is still possible - it
  just fails this check, so a human must read the diff and approve past it."
  (:require [babashka.process :refer [sh]]
            [clojure.edn :as edn]
            [clojure.set :as set]))

(defn- read-at [ref path]
  (let [{:keys [exit out]} (sh "git" "show" (str ref ":" path))]
    (when (zero? exit) (edn/read-string out))))

(defn crap-weakening [base head]
  (let [b (:crap-max base) h (:crap-max head)]
    (cond
      (nil? b)  nil
      (nil? h)  "quality-gates.edn: :crap-max was removed"
      (> h b)   (str "quality-gates.edn: :crap-max raised from " b " to " h))))

(defn- new-allowances [base head]
  (for [[component allowed] (:allowed-dependencies head)
        :let [before (get-in base [:allowed-dependencies component])]
        :when (or (= :all allowed)
                  (seq (set/difference (set allowed) (set before))))]
    (str "dependency-checker.edn: " component " may now depend on "
         (if (= :all allowed) ":all" (vec (set/difference (set allowed) (set before)))))))

(defn dependency-weakenings [base head]
  (when base
    (concat
      (new-allowances base head)
      (for [k [:allowed-exceptions :ignored-components]
            :when (seq (set/difference (set (k head)) (set (k base))))]
        (str "dependency-checker.edn: new " k " entries"))
      (for [k [:fail-on-violations :fail-on-cycles]
            :when (and (k base) (not (k head)))]
        (str "dependency-checker.edn: " k " turned off")))))

(defn check
  "Returns an exit code: 0 no weakening, 1 weakening found."
  [base-ref]
  (let [problems (remove nil?
                         (concat
                           [(crap-weakening (read-at base-ref "quality-gates.edn")
                                            (edn/read-string (slurp "quality-gates.edn")))]
                           (dependency-weakenings (read-at base-ref "dependency-checker.edn")
                                                  (edn/read-string (slurp "dependency-checker.edn")))))]
    (if (empty? problems)
      (do (println "No quality gate was weakened against" base-ref) 0)
      (do (run! #(println (str "::error::" %)) problems)
          (println "A human must approve this weakening explicitly, with the reason in the PR.")
          1))))
