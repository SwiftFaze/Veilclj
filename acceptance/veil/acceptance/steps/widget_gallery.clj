(ns veil.acceptance.steps.widget-gallery
  "Acceptance steps for the widget gallery screen."
  (:require [clojure.string :as str]
            [veil.acceptance.step-support :refer [ok check fail]]
            [veil.game.state :as state]
            [veil.ui.buffer :as buffer]
            [veil.ui.widgets :as widgets]))

(defn- int-of [s]
  (Long/parseLong s))

(defn- guarded
  "Wrap a handler so a bad input or a broken step becomes a failed step."
  [handler]
  (fn [world groups]
    (try
      (handler world groups)
      (catch Throwable t
        (fail (str "step raised " (.getSimpleName (class t)) ": " (.getMessage t)))))))

(defn- with-buffer
  "Run (f buf) when the world has a buffer, else fail saying so."
  [world f]
  (if-let [buf (:buffer @world)]
    (f buf)
    (fail "no buffer has been made in this scenario")))

(defn- find-text
  "[col row] of the first place text appears in a row of buf, or nil."
  [buf text]
  (first (for [row (range (:rows buf))
               :let [col (str/index-of (buffer/row-text buf row) text)]
               :when col]
           [col row])))

(defn- cells-of
  "The cells a text occupies once found at col, row."
  [buf text col row]
  (map #(buffer/cell buf % row) (range col (+ col (count text)))))

(def ^:private raw-handlers
  [;; Gallery status message
   [#"the gallery's status message is \"([^\"]*)\""
    (fn [world [_ expected]]
      (let [s (:state @world)
            actual (get-in s [:gallery :status])]
        (check (= expected actual)
               (str "gallery's status message is \"" actual
                    "\", expected \"" expected "\""))))]

   [#"the gallery has no status message"
    (fn [world _]
      (let [s (:state @world)
            actual (get-in s [:gallery :status])]
        (check (nil? actual)
               (str "gallery has status message: \"" actual "\""))))]

   ;; Hints shown
   [#"the hints shown are (.+)"
    (fn [world [_ hints-str]]
      (let [expected-strs (str/split hints-str #",\s*")
            actual-hints (state/gallery-hints)]
        (check (= (count expected-strs) (count actual-hints))
               (str "expected " (count expected-strs) " hints, got " (count actual-hints))))
      (ok))]

   ;; Keycap at location (for the hint bar)
   [#"the keycap \"([^\"]*)\" is at column (\d+), row (\d+)"
    (fn [world [_ keycap-text col row]]
      (with-buffer world
        (fn [buf]
          (let [expected-col (int-of col)
                expected-row (int-of row)
                actual-cell (buffer/cell buf expected-col expected-row)]
            (check (= keycap-text (:glyph actual-cell))
                   (str "keycap at column " col ", row " row " is \"" (:glyph actual-cell)
                        "\", expected \"" keycap-text "\"")))))
      (ok))]

   ;; Label at location (for the hint bar)
   [#"the label \"([^\"]*)\" is at column (\d+), row (\d+) in (\w+) on (\w+)"
    (fn [world [_ label col row fg bg]]
      (with-buffer world
        (fn [buf]
          (let [expected-col (int-of col)
                expected-row (int-of row)
                cells (cells-of buf label expected-col expected-row)]
            (check (every? #(and (= (keyword fg) (:fg %))
                                (= (keyword bg) (:bg %)))
                          cells)
                   (str "\"" label "\" at column " col ", row " row " colors mismatch")))))
      (ok))]
   ])

(def handlers
  (mapv (fn [[pattern handler]] [pattern (guarded handler)]) raw-handlers))
