(ns veil.ui.qa.launch
  "Parse command-line arguments for QA runs: --keys, --log.")

(defn parse-args
  "Parse launch arguments into {:keys path :log path} or {:error \"...\"}."
  [args]
  (loop [remaining args
         result {:keys nil :log nil}]
    (if (empty? remaining)
      result
      (let [arg (first remaining)
            tail (clojure.core/rest remaining)]
        (cond
          (= arg "--keys")
          (if (empty? tail)
            {:error "--keys needs a file path"}
            (let [path (first tail)
                  remaining-after (clojure.core/rest tail)]
              (if (clojure.string/starts-with? path "--")
                {:error "--keys needs a file path"}
                (recur remaining-after (assoc result :keys path)))))

          (= arg "--log")
          (if (empty? tail)
            {:error "--log needs a file path"}
            (let [path (first tail)
                  remaining-after (clojure.core/rest tail)]
              (if (clojure.string/starts-with? path "--")
                {:error "--log needs a file path"}
                (recur remaining-after (assoc result :log path)))))

          (clojure.string/starts-with? arg "--")
          {:error (str "unknown argument " arg)}

          :else
          {:error (str "unknown argument " arg)})))))
