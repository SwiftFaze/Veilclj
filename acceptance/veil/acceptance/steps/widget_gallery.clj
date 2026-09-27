(ns veil.acceptance.steps.widget-gallery
  "Acceptance steps for the widget gallery screen."
  (:require [clojure.string :as str]
            [veil.acceptance.step-support :refer [ok check fail]]
            [veil.game.state :as state]
            [veil.ui.buffer :as buffer]
            [veil.ui.widgets :as widgets]
            [veil.ui.commands :as commands]))

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

(defn- with-frame
  "Run (f frame) when draw commands were built, else fail with why not."
  [world f]
  (if-let [frame (:frame @world)]
    (f frame)
    (fail (str "no draw commands were built"
               (when-let [error (:commands-error @world)]
                 (str " (building them failed: " error ")"))))))

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
               (str "expected " (count expected-strs) " hints, got " (count actual-hints)))))]

   ;; Keycap at location (for the hint bar) - check multi-char text
   [#"the keycap \"([^\"]*)\" is at column (\d+), row (\d+)"
    (fn [world [_ keycap-text col row]]
      (with-buffer world
        (fn [buf]
          (let [expected-col (int-of col)
                expected-row (int-of row)
                actual-text (apply str (map :glyph
                                           (map #(buffer/cell buf % expected-row)
                                                (range expected-col (+ expected-col (count keycap-text))))))]
            (check (= keycap-text actual-text)
                   (str "keycap at column " col ", row " row " is \"" actual-text
                        "\", expected \"" keycap-text "\""))))))]

   ;; Label at location (for the hint bar)
   [#"the label \"([^\"]*)\" is at column (\d+), row (\d+) in (\w+) on (\w+)"
    (fn [world [_ label col row fg bg]]
      (with-buffer world
        (fn [buf]
          (let [expected-col (int-of col)
                expected-row (int-of row)
                cells (cells-of buf label expected-col expected-row)
                actual-text (apply str (map :glyph cells))]
            (check (and (= label actual-text)
                        (every? #(and (= (keyword fg) (:fg %))
                                     (= (keyword bg) (:bg %)))
                               cells))
                   (str "\"" actual-text "\" at column " col ", row " row
                        " in " (mapv (juxt :fg :bg) cells)
                        ", expected \"" label "\" in " fg " on " bg))))))]

   ;; Build draw commands (reuse grid.clj's infrastructure)
   [#"the buffer is turned into draw commands for cells (\d+) by (\d+) pixels"
    (fn [world [_ cell-w cell-h]]
      (with-buffer world
        (fn [buf]
          (let [built (try (commands/frame (:state @world) buf (int-of cell-w) (int-of cell-h))
                           (catch Exception e {:error (.getMessage e)}))]
            (if-let [error (:error built)]
              (do (swap! world #(-> % (assoc :commands-error error) (dissoc :frame)))
                  (ok))
              (do (swap! world #(-> % (assoc :frame built :cell-size [(int-of cell-w) (int-of cell-h)])
                                    (dissoc :commands-error)))
                  (ok)))))))]

   [#"building the draw commands succeeds"
    (fn [world _]
      (with-frame world
        (fn [frame]
          (check (and (:rects frame) (:glyphs frame) true)
                 "draw commands were built successfully"))))]
   ])

(def handlers
  (mapv (fn [[pattern handler]] [pattern (guarded handler)]) raw-handlers))
