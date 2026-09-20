(ns veil-tools.single-answer
  "SPIKE: a probabilistic check for the 'Single answer' rule in
  docs/clean-code-gate.md — a veil.ui function must not work out an answer that
  veil.game already provides. docs/architecture.md says outright that no tool
  detects this; this asks a TypeSafe System One model (Jev) instead, and is
  advisory only.

  Pure: every function here is data in, data out. The HTTP call is injected as
  `ask` (veil-tools.jev/ask in the bb task), the way veil.ui.qa.session takes
  its I/O functions, so the payload and the verdict logic are testable without
  a network."
  (:require [clojure.string :as str]
            [veil-tools.clj-source :as clj-source]))

(def ^:private defn-heads #{'defn 'defn-})

(defn- ns-symbol [forms]
  (some #(when (and (seq? %) (= 'ns (first %))) (second %)) forms))

(defn- body-after-name
  "A defn's forms past the name, docstring and attribute map."
  [form]
  (cond-> (drop 2 form)
    (string? (nth form 2 nil)) rest
    :always (as-> body (if (map? (first body)) (rest body) body))))

(defn- arglists [form]
  (let [body (body-after-name form)]
    (if (vector? (first body))
      [(first body)]
      (vec (keep #(when (seq? %) (first %)) body)))))

(defn- docstring [form]
  (let [after-name (nth form 2 nil)]
    (when (string? after-name) after-name)))

(defn- source-slice [lines start end]
  (->> (subvec lines (dec start) (min end (count lines)))
       (str/join "\n")
       str/trimr))

(defn definitions
  "Every defn/defn- in one file's source text, as
  [{:ns :name :private? :doc :arglists :line :source}]. The source text is
  sliced from the file rather than printed from the form, so the model sees the
  code as written, comments and all."
  [source-text]
  (let [forms (clj-source/read-forms source-text)
        lines (vec (str/split-lines source-text))
        nsym (ns-symbol forms)
        tops (vec (filter #(and (seq? %) (:line (meta %))) forms))
        starts (mapv #(:line (meta %)) tops)]
    (vec (for [[i form] (map-indexed vector tops)
               :when (defn-heads (first form))
               :let [start (nth starts i)
                     end (dec (nth starts (inc i) (inc (count lines))))]]
           {:ns nsym
            :name (second form)
            :private? (= 'defn- (first form))
            :doc (docstring form)
            :arglists (arglists form)
            :line start
            :source (source-slice lines start end)}))))

(defn- qualified [definition]
  (str (:ns definition) "/" (:name definition)))

(defn game-api
  "The candidate answers: every public veil.game function, as
  [{:name :arglists :doc}]. files is path -> source text. A private function
  can't be the one a ui function should have called, so it is left out — and a
  candidate the model never sees is one it can never choose."
  [files]
  (vec (for [[_ source] (sort files)
             definition (definitions source)
             :when (not (:private? definition))]
         {:name (qualified definition)
          :arglists (mapv str (:arglists definition))
          :doc (:doc definition)})))

(defn ui-functions
  "Every function defined in the given ui files, as the things to judge.
  files is path -> source text; the path rides along so a finding can name it."
  [files]
  (vec (for [[path source] (sort files)
             definition (definitions source)]
         (assoc definition :path path))))

(def ^:private rule
  {:rule (str "In this codebase dependencies point one way: veil.main -> veil.ui "
              "-> veil.game. veil.ui translates; it does not decide. When "
              "veil.game already answers a question, veil.ui must call it and "
              "translate the result into glyphs or key events.")
   :defect (str "Working the same answer out again from the raw facts is a defect "
                "even though the dependency arrow still points downward, because "
                "the rule now exists in two places and the copies drift apart.")
   :not_a_defect (str "Presentation work is veil.ui's own job and is not a defect: "
                      "layout arithmetic, centering, pixel positions, choosing "
                      "glyphs or colour keys, laying out screen text, and parsing "
                      "or formatting files that belong to the ui.")})

(defn- api-criteria [api]
  (into {"none" (str "The ui function does not re-derive any game answer, or the "
                     "answer it works out is not one of the listed functions.")}
        (map (juxt :name #(or (:doc %) "(no docstring)")) api)))

(defn payload
  "The request body for one ui function: both questions asked over one state, so
  they run in parallel. The noul carries the verdict; the choice names the
  veil.game function the finding has to cite as evidence."
  [ui-fn api]
  {:model "jev-latest"
   :state {:ui_function {:namespace (str (:ns ui-fn))
                         :name (str (:name ui-fn))
                         :source (:source ui-fn)}
           :game_api api}
   :questions
   {:rederives
    {:type "noul"
     :instructions (assoc rule
                          :judge (str "Judge `ui_function.source`. Does it work out an "
                                      "answer that one of the functions in `game_api` "
                                      "already provides, instead of calling that function?"))
     :criteria
     {:true (str "The function computes a game-level answer itself — repeating a rule, "
                 "a selection, a transition or a classification that a listed game_api "
                 "function already returns.")
      :false (str "The function calls the game function for every game-level answer it "
                  "needs, or it only does presentation work that veil.game does not "
                  "answer at all.")}}
    :duplicated_function
    {:type "choice"
     :instructions (str "Which function in `game_api` already provides the answer that "
                        "`ui_function.source` works out for itself? Answer none if it "
                        "does not re-derive any of them.")
     :criteria (api-criteria api)}}})

(defn finding
  "One ui function's answers turned into a finding map, or nil when the model is
  below the reporting threshold. Keeps the raw probability: the disposition is
  a human's, and a rounded verdict would hide a 0.51 next to a 0.99."
  [ui-fn answers threshold]
  (let [probability (get-in answers [:rederives :noul])
        duplicates (get-in answers [:duplicated_function :choice])]
    (when (and (number? probability) (>= probability threshold))
      {:path (:path ui-fn)
       :line (:line ui-fn)
       :function (qualified ui-fn)
       :probability probability
       :duplicates (when (not= "none" duplicates) duplicates)
       :confidence (get-in answers [:duplicated_function :confidence])})))

(defn- render [{:keys [path line function probability duplicates]}]
  (format "  %.2f  %s (%s line %d)%s"
          (double probability) function path line
          (if duplicates
            (str "\n          already answered by " duplicates)
            "\n          no game_api function named; likely presentation work")))

(defn report
  "The advisory section's text: findings worst first, or a clean line."
  [findings]
  (if (empty? findings)
    ["Single answer (advisory): no veil.ui function looks like it re-derives a veil.game answer."]
    (concat [(str "Single answer (advisory): " (count findings)
                  " veil.ui function(s) may re-derive a veil.game answer.")
             "Probability that the function works out an answer veil.game already gives:"]
            (map render (sort-by (comp - :probability) findings))
            [""
             "Advisory only. Each line needs a disposition in the completion report:"
             "fix it by calling veil.game, or one line on why it is correct as written."])))

(defn check
  "ask is (payload -> parsed response). Returns
  {:findings [...] :errors [...] :usage {:input_tokens n :output_tokens n}}.
  One request per ui function: each has its own state, so they cannot share one."
  [ask ui-fns api threshold]
  (reduce (fn [acc ui-fn]
            (let [response (ask (payload ui-fn api))]
              (if-let [error (:error response)]
                (update acc :errors conj (str (qualified ui-fn) ": " error))
                (let [hit (finding ui-fn (:answers response) threshold)]
                  (-> acc
                      (cond-> hit (update :findings conj hit))
                      (update-in [:usage :input_tokens] + (get-in response [:usage :input_tokens] 0))
                      (update-in [:usage :output_tokens] + (get-in response [:usage :output_tokens] 0)))))))
          {:findings [] :errors [] :usage {:input_tokens 0 :output_tokens 0}}
          ui-fns))
