(ns veil-tools.shell-check
  "Mechanical check that veil.main and veil.ui.draw, the Quil shell, decide
  nothing and calculate nothing, so everything they leave uncovered is a call
  into Quil or Processing. The rule is in docs/testing.md.

  Pure: `check` takes the source text of the shell files and returns findings.
  Source is read with the Clojure reader, so comments and strings are ignored."
  (:require [clojure.string :as str]
            [veil-tools.clj-source :as clj-source]))

(def shell-files
  "The only files the check looks at."
  ["src/veil/main.clj" "src/veil/ui/draw.clj"])

(def ^:private calculation-ops
  #{'+ '- '* '/ 'inc 'dec 'mod 'quot 'rem '= 'not= '< '> '<= '>=})

(def ^:private branch-heads #{'cond 'case 'condp})

(def ^:private guard-heads #{'if 'when 'if-not 'when-not})

(defn- require-aliases
  "alias symbol -> namespace symbol from the file's first ns form."
  [forms]
  (let [ns-form (first (filter #(and (seq? %) (= 'ns (first %))) forms))]
    (into {} (for [clause (rest ns-form)
                   :when (and (seq? clause) (= :require (first clause)))
                   spec (rest clause)
                   :when (vector? spec)
                   [option value] (partition 2 (rest spec))
                   :when (= :as option)]
               [value (first spec)]))))

(defn- veil-namespace?
  "Whether sym is qualified with a veil.* namespace, directly or by alias."
  [sym aliases]
  (let [qualifier (some-> (namespace sym) symbol)]
    (and qualifier
         (str/starts-with? (str (get aliases qualifier qualifier)) "veil."))))

(defn- keyword-lookup? [form]
  (and (seq? form) (= 2 (count form)) (keyword? (first form)) (symbol? (second form))))

(defn- simple-value? [form]
  (or (symbol? form) (keyword-lookup? form)))

(defn- veil-call? [form aliases]
  (and (seq? form)
       (symbol? (first form))
       (veil-namespace? (first form) aliases)
       (every? simple-value? (rest form))))

(defn- allowed-guard-test?
  "A guard may test one already-computed value: a name, a keyword lookup of a
  name, or a veil.* function called on such values."
  [guard-test aliases]
  (or (simple-value? guard-test) (veil-call? guard-test aliases)))

(defn- own-finding
  "The message for form itself (not its children), or nil."
  [form aliases]
  (when (seq? form)
    (let [head (first form)]
      (cond
        (and (guard-heads head)
             (some? (second form))
             (not (allowed-guard-test? (second form) aliases))) "a guard on an expression"
        (calculation-ops head) (str "calculation with " head)
        (branch-heads head) "a multi-way branch"))))

(defn- children
  "What to look inside. A guard's test is skipped: it is reported (or allowed)
  as a whole, and looking inside would report the same expression twice."
  [form]
  (cond
    (and (seq? form) (guard-heads (first form))) (drop 2 form)
    (coll? form) (seq form)))

(defn- form-findings
  "[{:line n :message m}] for form and everything inside it."
  [form aliases]
  (let [message (own-finding form aliases)]
    (concat (when message [{:line (:line (meta form)) :message message}])
            (mapcat #(form-findings % aliases) (children form)))))

(defn- file-findings [path source-text]
  (if (nil? source-text)
    [{:path path :message "file not found"}]
    (try
      (let [forms (clj-source/read-forms source-text)
            aliases (require-aliases forms)]
        (vec (for [form forms
                   finding (form-findings form aliases)]
               (assoc finding :path path))))
      (catch Exception _
        [{:path path :message "could not be read"}]))))

(defn- render [{:keys [path line message]}]
  (if line
    (str path " line " line ": " message)
    (str path ": " message)))

(defn check
  "files is a map of path -> source text. Only the shell files are looked at; a
  missing one is itself a finding, so renaming a shell file can't switch the
  check off. Returns {:findings [\"<path> line <n>: <message>\" ...]
  :exit-status 0-or-1}, findings ordered by path, then line."
  [files]
  (let [findings (->> shell-files
                      (mapcat #(file-findings % (get files %)))
                      (sort-by (juxt :path #(or (:line %) 0)))
                      (mapv render))]
    {:findings findings
     :exit-status (if (seq findings) 1 0)}))
