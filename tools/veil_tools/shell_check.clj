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

(defn- veil-ns? [sym]
  "Check if a symbol refers to a veil.* namespace (qualified or via alias)."
  (if-let [ns (namespace sym)]
    (.startsWith ns "veil.")
    false))

(defn- valid-guard-test? [test]
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
        (and (symbol? head) (veil-ns? head))
        ; All args must be symbols or keyword lookups of symbols
        (every? (fn [arg]
                  (or (symbol? arg)
                      (and (list? arg) (keyword? (first arg)) (= (count arg) 2) (symbol? (second arg)))))
                args)
        :else false))
    :else false))

(defn- read-all-forms [source-text]
  "Read all forms from source text. Returns vector of forms with line metadata."
  (try
    (let [reader (LineNumberingPushbackReader. (java.io.StringReader. source-text))
          forms (atom [])]
      (loop []
        (let [result (try
                       (clojure.lang.LispReader/read reader false ::eof nil)
                       (catch Exception _ ::error))]
          (cond
            (= result ::error) @forms
            (= result ::eof) @forms
            :else (do
                    (swap! forms conj result)
                    (recur)))))
      @forms)
    (catch Exception _ [])))

(defn- find-violations-in-form [form]
  "Find all violations (guards, calculations, branches) in a form.
  Returns vector of [line message] pairs."
  (let [violations (atom [])]
    (letfn [(walk [f]
              (cond
                (list? f)
                (let [head (first f)
                      line (:line (meta f))]
                  (cond
                    ; Check for guard violations
                    (contains? guard-heads head)
                    (let [test (second f)]
                      (if (and test (not (valid-guard-test? test)))
                        (swap! violations conj [line "a guard on an expression"])))

                    ; Check for calculations
                    (contains? calculation-ops head)
                    (swap! violations conj [line (str "calculation with " (name head))])

                    ; Check for branches
                    (contains? branch-heads head)
                    (swap! violations conj [line "a multi-way branch"]))

                  ; Recurse into children (but not past guard violations)
                  (if (not (contains? guard-heads head))
                    (run! walk (rest f))))

                (vector? f) (run! walk f)
                (map? f) (run! walk (vals f))
                :else nil))]
      (walk form)
      @violations)))

(defn- analyze-source [path source-text]
  "Analyze a single source file and return findings."
  (if (nil? source-text)
    [[path 0 "file not found"]]
    (let [forms (read-all-forms source-text)]
      (vec (mapcat #(find-violations-in-form %) forms)))))

(defn check
  "Pure core: check files for shell violations.
  files is a map path -> source text.
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
                      (str path " line " line ": " message)))
               vec)]
      {:findings sorted-findings
       :exit-status (if (empty? sorted-findings) 0 1)})))
