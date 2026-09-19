(ns veil.acceptance.steps.crap-gate
  "Acceptance steps for the CRAP gate."
  (:require [crap4clj.crap :as crap]
            [veil-tools.crap-gate :as crap-gate]
            [veil.acceptance.step-support :refer [ok check fail arrange]]))

(defn- validate-offender-count-and-order
  [world expected-count]
  (let [offenders (get @world :offenders [])
        actual-count (count offenders)
        craps (mapv :crap offenders)
        is-descending (or (empty? craps) (= craps (sort > craps)))]
    (if (not= expected-count actual-count)
      (fail (str "expected " expected-count " offenders, got " actual-count))
      (if is-descending
        (ok)
        (fail (str "offenders not in descending order by CRAP: " craps))))))

(def ^:private step-handlers
  [[#"the CRAP limit is (\d+)"
    (fn [world [_ limit-str]]
      (arrange world :crap-limit (Long/parseLong limit-str)))]

   [#"a function with complexity (\d+) and (\d+)% coverage"
    (fn [world [_ cc-str coverage-str]]
      (let [cc (Long/parseLong cc-str)
            coverage (Long/parseLong coverage-str)
            crap-score (crap/crap-score cc coverage)
            entry-count (count (get @world :crap-entries []))
            fn-name (str "fn-" entry-count)
            entry {:namespace "example" :name fn-name :complexity cc :coverage coverage :crap crap-score}]
        (swap! world update :crap-entries (fnil conj []) entry)
        (swap! world assoc :last-fn-name fn-name)
        (ok)))]

   [#"the CRAP gate runs"
    (fn [world _]
      (let [entries (get @world :crap-entries [])
            limit (get @world :crap-limit)]
        (swap! world assoc :offenders (crap-gate/offenders entries limit))
        (ok)))]

   [#"it passes"
    (fn [world _]
      (let [offenders (get @world :offenders [])]
        (check (empty? offenders)
               (str "expected no offenders, but found: "
                    (mapv (fn [o] (select-keys o [:name :crap])) offenders)))))]

   [#"it fails"
    (fn [world _]
      (let [offenders (get @world :offenders [])]
        (check (not (empty? offenders))
               (str "expected offenders, but none found"))))]

   [#"the failure names that function"
    (fn [world _]
      (let [offender-names (mapv :name (get @world :offenders []))
            last-fn-name (get @world :last-fn-name)]
        (check (some #{last-fn-name} offender-names)
               (str "expected '" last-fn-name "' to be in offenders: " offender-names))))]

   [#"the CRAP score is ([\d.]+)"
    (fn [world [_ score-str]]
      (let [expected (Double/parseDouble score-str)
            entries (get @world :crap-entries [])
            actual (when (= 1 (count entries)) (:crap (first entries)))]
        (if (and actual (< (Math/abs (- expected actual)) 1e-9))
          (ok)
          (fail (str "expected CRAP score " expected ", got " actual)))))]

   [#"it reports (\d+) offenders, worst first"
    (fn [world [_ count-str]]
      (validate-offender-count-and-order world (Long/parseLong count-str)))]])

(def handlers step-handlers)
