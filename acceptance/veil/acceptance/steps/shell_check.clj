(ns veil.acceptance.steps.shell-check
  "Acceptance steps for shell-check scenarios."
  (:require [veil.acceptance.step-support :refer [ok check fail]]
            [veil-tools.shell-check :as shell-check]))

(def handlers
  [[#"src/veil/main.clj contains the form (.+)"
    (fn [world [_ form-str]]
      (let [source (str "(ns test (:require [quil.core :as q] [veil.ui.input :as input]))\n" form-str)]
        (swap! world assoc :files {"src/veil/main.clj" source
                                   "src/veil/ui/draw.clj" "(ns test2)"}))
      (ok))]

   [#"src/veil/ui/draw.clj contains the form (.+)"
    (fn [world [_ form-str]]
      (let [source (str "(ns test (:require [quil.core :as q] [veil.ui.input :as input]))\n" form-str)]
        (swap! world assoc :files {"src/veil/main.clj" "(ns test)"
                                   "src/veil/ui/draw.clj" source}))
      (ok))]

   [#"src/veil/ui/view.clj contains the form (.+)"
    (fn [world [_ form-str]]
      (swap! world assoc :files {"src/veil/main.clj" "(ns test)"
                                 "src/veil/ui/draw.clj" "(ns test2)"
                                 "src/veil/ui/view.clj" form-str})
      (ok))]

   [#"src/veil/ui/draw.clj has the lines \"(.+)\" and \"(.+)\" after its ns form"
    (fn [world [_ line1 line2]]
      (let [source (str "(ns test)\n" line1 "\n" line2)]
        (swap! world assoc :files {"src/veil/main.clj" "(ns test)"
                                   "src/veil/ui/draw.clj" source}))
      (ok))]

   [#"src/veil/main.clj contains the text \"(.+)\" as a comment"
    (fn [world [_ text]]
      (let [source (str "(ns test)\n;; " text)]
        (swap! world assoc :files {"src/veil/main.clj" source
                                   "src/veil/ui/draw.clj" "(ns test2)"}))
      (ok))]

   [#"src/veil/ui/draw.clj contains the text \"(.+)\" as a docstring"
    (fn [world [_ text]]
      (let [source (str "(ns test)\n(defn f \"" text "\" [])")]
        (swap! world assoc :files {"src/veil/main.clj" "(ns test)"
                                   "src/veil/ui/draw.clj" source}))
      (ok))]

   [#"src/veil/main.clj is present and src/veil/ui/draw.clj is absent"
    (fn [world _]
      (swap! world assoc :files {"src/veil/main.clj" "(ns test)"})
      (ok))]

   [#"src/veil/ui/draw.clj is present and src/veil/main.clj is absent"
    (fn [world _]
      (swap! world assoc :files {"src/veil/ui/draw.clj" "(ns test)"})
      (ok))]

   [#"the shell check runs"
    (fn [world _]
      (let [files (:files @world)
            result (shell-check/check files)]
        (swap! world assoc :shell-check-result result))
      (ok))]

   [#"there are no findings"
    (fn [world _]
      (let [result (:shell-check-result @world)
            findings (:findings result)]
        (check (empty? findings) "no findings")))]

   [#"the only finding is (.+)"
    (fn [world [_ expected]]
      (let [result (:shell-check-result @world)
            findings (:findings result)]
        (check (and (= 1 (count findings)) (= expected (first findings)))
               (str "only finding is: " expected))))]

   [#"the findings are (.+)"
    (fn [world [_ expected-str]]
      (let [expected-lines (clojure.string/split expected-str #", ")
            result (:shell-check-result @world)
            findings (:findings result)]
        (check (= expected-lines findings)
               (str "findings match: " (clojure.string/join ", " findings)))))]

   [#"the shell check exits with status (\d+)"
    (fn [world [_ status-str]]
      (let [result (:shell-check-result @world)
            expected (Integer/parseInt status-str)
            actual (:exit-status result)]
        (check (= expected actual)
               (str "exit status is " actual))))]
   ])
