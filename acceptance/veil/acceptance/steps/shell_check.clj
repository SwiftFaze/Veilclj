(ns veil.acceptance.steps.shell-check
  "Acceptance steps for the shell check scenarios in thin-quil-shell.feature.
  The world holds a map of path -> source text. It starts with both shell files
  present and clean, so a scenario that names one file isn't also reporting the
  other as missing; only the 'is absent' scenarios remove a file."
  (:require [clojure.string :as str]
            [veil-tools.shell-check :as shell-check]
            [veil.acceptance.step-support :refer [ok check]]))

(def ^:private ns-form
  "(ns veil.main (:require [quil.core :as q] [veil.ui.input :as input]))")

(defn- clean-files []
  {"src/veil/main.clj" "(ns veil.main)"
   "src/veil/ui/draw.clj" "(ns veil.ui.draw)"})

(defn- put-source!
  "Give path the ns form on line 1 and each of lines from line 2."
  [world path & lines]
  (swap! world update :files #(assoc (or % (clean-files)) path (str/join "\n" (cons ns-form lines))))
  (ok))

(defn- only-present!
  [world path]
  (swap! world assoc :files {path "(ns present)"})
  (ok))

(defn- findings [world]
  (:findings (:shell-check-result @world)))

(def handlers
  [[#"(src/veil/\S+\.clj) contains the form (.+)"
    (fn [world [_ path form]]
      (put-source! world path form))]

   [#"(src/veil/\S+\.clj) has the lines \"([^\"]+)\" and \"([^\"]+)\" after its ns form"
    (fn [world [_ path line-1 line-2]]
      (put-source! world path line-1 line-2))]

   [#"(src/veil/\S+\.clj) contains the text \"([^\"]+)\" as a comment"
    (fn [world [_ path text]]
      (put-source! world path (str ";; " text)))]

   [#"(src/veil/\S+\.clj) contains the text \"([^\"]+)\" as a docstring"
    (fn [world [_ path text]]
      (put-source! world path (str "(defn f \"" text "\" [] nil)")))]

   [#"(src/veil/\S+\.clj) is present and src/veil/\S+\.clj is absent"
    (fn [world [_ present]]
      (only-present! world present))]

   [#"the shell check runs"
    (fn [world _]
      (swap! world assoc :shell-check-result (shell-check/check (or (:files @world) (clean-files))))
      (ok))]

   [#"there are no findings"
    (fn [world _]
      (check (empty? (findings world))
             (str "expected no findings, got " (pr-str (findings world)))))]

   [#"the only finding is (.+)"
    (fn [world [_ expected]]
      (check (= [expected] (findings world))
             (str "expected only " (pr-str expected) ", got " (pr-str (findings world)))))]

   [#"the findings are (.+)"
    (fn [world [_ expected]]
      (check (= (str/split expected #", ") (findings world))
             (str "expected " (pr-str expected) ", got " (pr-str (findings world)))))]

   [#"the shell check exits with status (\d+)"
    (fn [world [_ status]]
      (let [actual (:exit-status (:shell-check-result @world))]
        (check (= (parse-long status) actual)
               (str "expected exit status " status ", got " actual))))]])
