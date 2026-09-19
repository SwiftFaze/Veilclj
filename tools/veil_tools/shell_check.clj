(ns veil-tools.shell-check
  "Mechanical check that veil.main and veil.ui.draw make no decisions or calculations.
  Reads Clojure source and analyzes forms, so comments and strings are ignored."
  (:require [clojure.string :as str])
  (:import [clojure.lang LineNumberingPushbackReader]))

(def ^:private shell-files
  ["src/veil/main.clj" "src/veil/ui/draw.clj"])

(def ^:private calculation-ops
  #{'+ '- '* '/ 'inc 'dec 'mod 'quot 'rem '= 'not= '< '> '<= '>=})

(def ^:private branch-heads
  #{'cond 'case 'condp})

(def ^:private guard-heads
  #{'if 'when 'if-not 'when-not})

(defn- read-next
  "Read one form from the reader, returning ::eof if at end."
  [reader]
  (clojure.lang.LispReader/read reader false ::eof nil))

(defn- read-all-forms
  "Read all forms from source text. Returns vector of forms with line metadata."
  [source-text]
  (let [reader (LineNumberingPushbackReader. (java.io.StringReader. source-text))
        forms (atom [])]
    (loop []
      (let [form (read-next reader)]
        (if (= form ::eof)
          @forms
          (do
            (swap! forms conj form)
            (recur)))))))

(defn- parse-ns-aliases
  "Extract :require aliases from the ns form. Returns a map of alias -> namespace."
  [ns-form]
  (try
    (if (and (list? ns-form) (= (first ns-form) 'ns))
      (let [clauses (drop 2 ns-form)]
        (reduce (fn [acc clause]
                  (if (and (vector? clause) (= (first clause) :require))
                    (let [require-clauses (rest clause)]
                      (reduce (fn [acc2 req]
                                (cond
                                  (vector? req)
                                  (let [ns (first req)
                                        rest-parts (rest req)
                                        as-clause (some (fn [[k v]] (when (= k :as) v)) (partition 2 rest-parts))]
                                    (if as-clause
                                      (assoc acc2 as-clause ns)
                                      acc2))
                                  :else acc2))
                              acc
                              require-clauses))
                    acc))
                {}
                clauses))
      {})
    (catch Exception _ {})))

(defn- veil-ns? [sym alias-map]
  "Check if a symbol refers to a veil.* namespace (qualified or via alias)."
  (if (symbol? sym)
    (if-let [ns (namespace sym)]
      (.startsWith (str ns) "veil.")
      (if-let [resolved (alias-map sym)]
        (.startsWith (str resolved) "veil.")
        false))
    false))

(defn- valid-guard-test? [test alias-map]
  "Check if a test form is allowed in a guard.
  Allowed: bare symbol, keyword lookup of one symbol, or veil.* function call with simple args."
  (cond
    (symbol? test) true
    (keyword? test) false
    (list? test)
    (let [head (first test)
          args (rest test)]
      (cond
        ; Keyword lookup like (:error launched)
        (and (keyword? head) (= (count args) 1) (symbol? (first args))) true
        ; Function call - must be veil.* namespace
        (and (symbol? head) (veil-ns? head alias-map))
        ; All args must be symbols or keyword lookups of symbols
        (every? (fn [arg]
                  (or (symbol? arg)
                      (and (list? arg) (keyword? (first arg)) (= (count arg) 2) (symbol? (second arg)))))
                args)
        :else false))
    :else false))

(defn- find-violations-in-form [form alias-map]
  "Find all violations (guards, calculations, branches) in a form.
  Returns vector of [line message] pairs."
  (let [violations (atom [])]
    (letfn [(walk [f]
              (cond
                (list? f)
                (let [head (first f)
                      line (:line (meta f))]
                  (cond
                    ; Check for guard violations (but don't recurse into the test)
                    (contains? guard-heads head)
                    (let [test (second f)]
                      (if (and test (not (valid-guard-test? test alias-map)))
                        (swap! violations conj [line "a guard on an expression"])))

                    ; Check for calculations
                    (contains? calculation-ops head)
                    (swap! violations conj [line (str "calculation with " (name head))])

                    ; Check for branches
                    (contains? branch-heads head)
                    (swap! violations conj [line "a multi-way branch"]))

                  ; Recurse into children (but skip the test of a guard to avoid double-reporting)
                  (if (not (contains? guard-heads head))
                    (run! walk (rest f))
                    (run! walk (drop 2 f))))

                (vector? f) (run! walk f)
                (map? f) (run! walk (vals f))
                :else nil))]
      (walk form)
      @violations)))

(defn- analyze-source [path source-text]
  "Analyze a single source file and return findings (including missing-file finding)."
  (if (nil? source-text)
    [[path nil "file not found"]]
    (try
      (let [forms (read-all-forms source-text)
            ns-form (first (filter #(and (list? %) (= (first %) 'ns)) forms))
            alias-map (parse-ns-aliases ns-form)]
        (vec (mapcat #(find-violations-in-form % alias-map) forms)))
      (catch Exception _
        [[path nil "could not be read"]]))))

(defn check
  "Pure core: check files for shell violations.
  files is a map path -> source text (nil means file not found).
  Returns {:findings [strings] :exit-status 0-or-1}"
  [files]
  (let [all-violations (atom [])]
    (doseq [path shell-files]
      (let [source (get files path)
            violations (analyze-source path source)]
        (run! #(swap! all-violations conj (vec (cons path %))) violations)))

    (let [sorted-findings
          (->> @all-violations
               (sort-by (fn [[path line _]]
                          [path (or line 0)]))
               (map (fn [[path line message]]
                      (if (nil? line)
                        (str path ": " message)
                        (str path " line " line ": " message))))
               vec)]
      {:findings sorted-findings
       :exit-status (if (empty? sorted-findings) 0 1)})))
