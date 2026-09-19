(ns veil-tools.shell-check-spec
  (:require [speclj.core :refer :all]
            [veil-tools.shell-check :as shell-check]))

(def ^:private main-path "src/veil/main.clj")
(def ^:private draw-path "src/veil/ui/draw.clj")

(def ^:private ns-form
  "(ns veil.main (:require [quil.core :as q] [veil.ui.input :as input]))")

(defn- source
  "A source file: the ns form on line 1, then each given line from line 2."
  [& lines]
  (apply str ns-form (map #(str "\n" %) lines)))

(defn- clean-files
  "Both shell files present and clean, with overrides merged over them."
  [& {:as overrides}]
  (merge {main-path "(ns veil.main)" draw-path "(ns veil.ui.draw)"} overrides))

(defn- findings [files]
  (:findings (shell-check/check files)))

(defn- main-findings [& lines]
  (findings (clean-files main-path (apply source lines))))

(defn- findings-by-form
  "form -> findings, so one failing form is named in the diff. Each form is
  placed on line 2 of src/veil/main.clj."
  [forms]
  (into {} (map (fn [form] [form (main-findings form)])) forms))

(describe "shell-check/check guards"
  (it "allows a guard on a bare symbol"
    (should= [] (main-findings "(when exit? (q/exit))")))

  (it "allows a guard on a keyword lookup of a symbol"
    (should= [] (main-findings "(if (:error launched) (die launched) (open-window))")))

  (it "allows a guard on a veil.* call through an alias"
    (should= [] (main-findings "(when (input/escape? event) (q/exit))")))

  (it "allows a guard on a fully-qualified veil.* call"
    (should= [] (main-findings "(when (veil.ui.input/escape? event) (q/exit))")))

  (it "allows a veil.* call whose arguments are keyword lookups"
    (should= [] (main-findings "(when (input/escape? (:event ctx)) (q/exit))")))

  (it "allows if-not and when-not on a bare symbol"
    (should= [] (main-findings "(if-not ready? (q/exit) nil)" "(when-not ready? (q/exit))")))

  (it "does not report a guard with no test"
    (should= [] (main-findings "(when)")))

  (it "reports a guard on an expression"
    (let [forms ["(when (= (char 27) (:raw-key event)) (q/exit))"
                 "(when (not exit?) (q/exit))"
                 "(if (and ready? exit?) (q/exit) nil)"
                 "(when (input/escape? (first events)) (q/exit))"
                 "(when (q/key-pressed?) (q/exit))"
                 "(when (escape? event) (q/exit))"
                 "(when :always (q/exit))"
                 "(when-not (not exit?) (q/exit))"
                 "(if-not (:error (:launched ctx)) (q/exit) nil)"]]
      (should= (zipmap forms (repeat [(str main-path " line 2: a guard on an expression")]))
               (findings-by-form forms))))

  (it "does not report the operators inside a flagged guard test"
    (should= [(str main-path " line 2: a guard on an expression")]
             (main-findings "(when (< (+ a 1) b) (q/exit))")))

  (it "still checks the branches of a flagged guard"
    (should= [(str main-path " line 2: a guard on an expression")
              (str main-path " line 2: calculation with +")]
             (main-findings "(when (= a b) (q/text t (+ y 1)))")))

  (it "checks the branches of an allowed guard"
    (should= [(str main-path " line 2: calculation with inc")]
             (main-findings "(when exit? (q/frame-rate (inc n)))")))

  (it "does not treat a bare alias as a veil.* namespace"
    (should= [(str main-path " line 2: a guard on an expression")]
             (main-findings "(when (input event) (q/exit))"))))

(describe "shell-check/check calculations"
  (it "reports every arithmetic and comparison operator"
    (let [ops '[+ - * / inc dec mod quot rem = not= < > <= >=]
          in-draw (fn [op] (findings (clean-files draw-path (source (str "(q/text text x (" op " y 1))")))))]
      (should= (zipmap ops (map #(vector (str draw-path " line 2: calculation with " %)) ops))
               (zipmap ops (map in-draw ops)))))

  (it "reports a calculation nested inside other calls"
    (should= [(str main-path " line 2: calculation with *")]
             (main-findings "(q/text t x (identity (* row 40)))")))

  (it "reports a calculation inside a vector and a map"
    (should= [(str main-path " line 2: calculation with +")
              (str main-path " line 3: calculation with -")]
             (main-findings "(q/fill [(+ a 1) 0 0])" "(q/fill {:r (- a 1)})")))

  (it "reports a calculation in an anonymous function"
    (should= [(str main-path " line 2: calculation with +")]
             (main-findings "(map #(+ % 1) xs)")))

  (it "does not report an operator that is only passed as a value"
    (should= [] (main-findings "(reduce + xs)"))))

(describe "shell-check/check multi-way branches"
  (it "reports cond, case and condp"
    (let [forms ["(cond a (q/exit) :else (q/frame-rate 30))"
                 "(case mode :a (q/exit) (q/frame-rate 30))"
                 "(condp = mode :a (q/exit))"]]
      (should= (zipmap forms (repeat [(str main-path " line 2: a multi-way branch")]))
               (findings-by-form forms))))

  (it "checks inside a multi-way branch"
    (should= [(str main-path " line 2: a multi-way branch")
              (str main-path " line 2: calculation with +")]
             (main-findings "(cond a (+ 1 2) :else nil)"))))

(describe "shell-check/check reading"
  (it "ignores comments"
    (should= [] (main-findings ";; (when (= a b) (q/exit))" "; (+ 1 2)")))

  (it "ignores strings and docstrings"
    (should= []
             (findings (clean-files draw-path (source "(defn f \"(+ 1 2) then (cond a b)\" [] nil)")))))

  (it "ignores a discarded form"
    (should= [] (main-findings "#_" "(+ 1 2)")))

  (it "reports the line of the offending form, not of its enclosing form"
    (should= [(str main-path " line 4: calculation with +")]
             (main-findings "(defn f []" "  (q/exit)" "  (q/text t (+ 1 2)))")))

  (it "reads every form in the file"
    (should= [(str main-path " line 2: calculation with +")
              (str main-path " line 3: calculation with -")]
             (main-findings "(+ 1 2)" "(- 3 4)")))

  (it "reports a file that cannot be read"
    (should= [(str main-path ": could not be read")]
             (findings (clean-files main-path "(ns veil.main) (when"))))

  (it "reports a file that cannot be read even when other files are fine"
    (should= [(str draw-path ": could not be read")]
             (findings (clean-files draw-path ")")))))

(describe "shell-check/check files"
  (it "passes when both shell files are clean"
    (should= {:findings [] :exit-status 0} (shell-check/check (clean-files))))

  (it "reports a missing shell file without a line"
    (should= [(str draw-path ": file not found")]
             (findings {main-path "(ns veil.main)"})))

  (it "reports each missing shell file"
    (should= [(str main-path ": file not found")
              (str draw-path ": file not found")]
             (findings {})))

  (it "ignores every other path in the map"
    (should= [] (findings (clean-files "src/veil/ui/view.clj" "(+ 50 (* row 40))"))))

  (it "orders findings by path, then by line"
    (should= [(str main-path " line 2: calculation with -")
              (str main-path " line 3: calculation with +")
              (str draw-path " line 2: calculation with *")]
             (findings (clean-files main-path (source "(- 1 2)" "(+ 1 2)")
                                    draw-path (source "(* 1 2)")))))

  (it "exits 1 when there is any finding"
    (should= 1 (:exit-status (shell-check/check (clean-files main-path (source "(+ 1 2)"))))))

  (it "exits 1 when a file is missing"
    (should= 1 (:exit-status (shell-check/check {}))))

  (it "exits 0 when there are no findings"
    (should= 0 (:exit-status (shell-check/check (clean-files main-path (source "(q/exit)")))))))
