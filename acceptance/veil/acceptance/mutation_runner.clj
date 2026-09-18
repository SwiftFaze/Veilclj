(ns veil.acceptance.mutation-runner
  "Persistent APS runner-worker: reads newline-delimited JSON job requests on
  stdin, runs the mutated feature IR, writes one JSON response per line.
  stdout carries protocol only (APS mutator-spec, 'Runner Adapter')."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test :as test]
            [veil.acceptance.runtime :as runtime])
  (:import [java.io BufferedReader StringWriter]))

(defn timeout-ms [timeout-text]
  (when-let [[_ amount unit] (re-matches #"(\d+)(ms|s|m)" (str/trim (or timeout-text "")))]
    (* (parse-long amount) ({"ms" 1 "s" 1000 "m" 60000} unit))))

(defn- run-feature [ir-path]
  (let [writer   (StringWriter.)
        failures (atom 0)]
    (binding [test/*test-out* writer]
      (with-redefs [test/report (fn [event]
                                  (when (#{:fail :error} (:type event))
                                    (swap! failures inc))
                                  (binding [*out* writer]
                                    (println (pr-str event))))]
        (runtime/assert-feature ir-path)))
    {:output (str writer) :failed? (pos? @failures)}))

(defn- response [request started outcome output error]
  {:id       (:id request)
   :outcome  outcome
   :output   output
   :error    error
   :duration (- (System/nanoTime) started)})

(defn run-request [request]
  (let [started (System/nanoTime)]
    (try
      (let [pending (future (run-feature (:feature_json request)))
            result  (if-let [ms (timeout-ms (:timeout request))]
                      (deref pending ms ::timeout)
                      @pending)]
        (if (= ::timeout result)
          (response request started "infrastructure_error" "" "acceptance mutation runner timed out")
          (response request started (if (:failed? result) "test_failure" "test_success")
                    (:output result) "")))
      (catch Throwable t
        (response request started "infrastructure_error" "" (str t))))))

(defn -main [& _]
  (try
    (doseq [line (line-seq (BufferedReader. *in*))]
      (println (json/write-str (run-request (json/read-str line :key-fn keyword))
                               :escape-slash false))
      (flush))
    (finally
      (shutdown-agents))))
