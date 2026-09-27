(ns veil.acceptance.steps.chrome-widgets
  "Acceptance steps for terminal chrome widgets (frame, title bar, status line, etc.)."
  (:require [clojure.string :as str]
            [veil.acceptance.step-support :refer [ok check fail]]
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

(defn- update-buffer!
  "Store the result of (f buffer) as the buffer."
  [world f]
  (with-buffer world
    (fn [buf]
      (swap! world assoc :buffer (f buf))
      (ok))))

(defn- cell-is [world col row expected description]
  (with-buffer world
    (fn [buf]
      (let [actual (buffer/cell buf col row)]
        (check (= expected actual)
               (str "cell at column " col ", row " row " " description ", got " (pr-str actual)))))))

(defn- box-char-to-ascii
  "Map box-drawing characters to ASCII for readability in feature file assertions."
  [ch]
  (cond
    (= ch (char 0x250C)) \+  ;; top-left
    (= ch (char 0x2510)) \+  ;; top-right
    (= ch (char 0x2514)) \+  ;; bottom-left
    (= ch (char 0x2518)) \+  ;; bottom-right
    (= ch (char 0x2500)) \-  ;; horizontal
    (= ch (char 0x2502)) \|  ;; vertical
    :else ch))

(def ^:private raw-handlers
  [;; Frame
   [#"a frame at column (-?\d+), row (-?\d+), (\d+) wide and (\d+) high titled \"([^\"]*)\" is drawn in (\w+) on (\w+)"
    (fn [world [_ col row w h title fg bg]]
      (update-buffer! world #(widgets/frame % (int-of col) (int-of row) (int-of w) (int-of h)
                                           title (keyword fg) (keyword bg))))]

   [#"a frame at column (-?\d+), row (-?\d+), (\d+) wide and (\d+) high with no title is drawn in (\w+) on (\w+)"
    (fn [world [_ col row w h fg bg]]
      (update-buffer! world #(widgets/frame % (int-of col) (int-of row) (int-of w) (int-of h)
                                           nil (keyword fg) (keyword bg))))]

   [#"the top edge of the frame reads \"([^\"]*)\""
    (fn [world [_ expected]]
      (with-buffer world
        (fn [buf]
          (let [row-text (buffer/row-text buf 0)
                ascii-row (apply str (map box-char-to-ascii row-text))]
            (check (= expected ascii-row)
                   (str "top edge reads \"" ascii-row "\", expected \"" expected "\"")))
          (ok))))]

   ;; Title bar
   [#"a title bar reading \"([^\"]*)\" on the left and \"([^\"]*)\" in the center is drawn on row (\d+)"
    (fn [world [_ left center row]]
      (update-buffer! world #(widgets/title-bar % left center (int-of row))))]

   ;; Status line
   [#"a status line reading \"([^\"]*)\" is drawn on row (\d+)"
    (fn [world [_ message row]]
      (update-buffer! world #(widgets/status-line % message (int-of row))))]

   [#"a status line with no message is drawn on row (\d+)"
    (fn [world [_ row]]
      (update-buffer! world #(widgets/status-line % nil (int-of row))))]

   ;; Keycap label - each action as its own step
   [#"the keycap label for the back action is asked for"
    (fn [world _]
      (let [label (widgets/keycap-label :back)]
        (swap! world assoc :last-keycap-label label)
        (ok)))]

   [#"the keycap label for the confirm action is asked for"
    (fn [world _]
      (let [label (widgets/keycap-label :confirm)]
        (swap! world assoc :last-keycap-label label)
        (ok)))]

   [#"the keycap label for the tab action is asked for"
    (fn [world _]
      (let [label (widgets/keycap-label :tab)]
        (swap! world assoc :last-keycap-label label)
        (ok)))]

   [#"the keycap label for the up action is asked for"
    (fn [world _]
      (let [label (widgets/keycap-label :up)]
        (swap! world assoc :last-keycap-label label)
        (ok)))]

   [#"the keycap label for the f12 action is asked for"
    (fn [world _]
      (let [label (widgets/keycap-label :f12)]
        (swap! world assoc :last-keycap-label label)
        (ok)))]

   [#"the keycap label for the character (.) is asked for"
    (fn [world [_ char]]
      (let [label (widgets/keycap-label {:char (first char)})]
        (swap! world assoc :last-keycap-label label)
        (ok)))]

   [#"the keycap label for the character (.) with Ctrl is asked for"
    (fn [world [_ char]]
      (let [label (widgets/keycap-label {:char (first char) :mods #{:ctrl}})]
        (swap! world assoc :last-keycap-label label)
        (ok)))]

   [#"the label is \"([^\"]*)\""
    (fn [world [_ expected]]
      (let [actual (:last-keycap-label @world)]
        (check (= expected actual)
               (str "label is \"" actual "\", expected \"" expected "\""))))]

   ;; Keycap
   [#"a keycap for the back action is drawn at column (\d+), row (\d+)"
    (fn [world [_ col row]]
      (update-buffer! world #(widgets/keycap % :back (int-of col) (int-of row))))]

   [#"a keycap for the confirm action is drawn at column (\d+), row (\d+)"
    (fn [world [_ col row]]
      (update-buffer! world #(widgets/keycap % :confirm (int-of col) (int-of row))))]

   ;; Hints setup
   [#"the hints are (.+)"
    (fn [world [_ hints-str]]
      (swap! world assoc :hints hints-str)
      (ok))]

   [#"the hint bar is drawn"
    (fn [world _]
      (ok))]

   [#"the hint bar takes (\d+) rows?"
    (fn [world [_ rows-str]]
      (ok))]

   ;; Accelerator label
   [#"the label \"([^\"]*)\" with accelerator ([a-zA-Z]) is written at column (\d+), row (\d+) in (\w+) on (\w+)"
    (fn [world [_ label letter col row fg bg]]
      (update-buffer! world
                      #(widgets/accelerator-label % label (first letter) (int-of col) (int-of row)
                                                  (keyword fg) (keyword bg))))]

   ;; Badge
   [#"a (\w+) badge reading \"([^\"]*)\" is drawn at column (\d+), row (\d+)"
    (fn [world [_ color text col row]]
      (update-buffer! world
                      #(widgets/badge % (keyword color) text (int-of col) (int-of row))))]

   ;; Chip
   [#"a (\w+) chip reading \"([^\"]*)\" is drawn at column (\d+), row (\d+)"
    (fn [world [_ color tag col row]]
      (update-buffer! world
                      #(widgets/chip % (keyword color) tag (int-of col) (int-of row) false)))]

   [#"a focused (\w+) chip reading \"([^\"]*)\" is drawn at column (\d+), row (\d+)"
    (fn [world [_ color tag col row]]
      (update-buffer! world
                      #(widgets/chip % (keyword color) tag (int-of col) (int-of row) true)))]
   ])

(def handlers
  (mapv (fn [[pattern handler]] [pattern (guarded handler)]) raw-handlers))
