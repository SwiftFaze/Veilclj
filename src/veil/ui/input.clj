(ns veil.ui.input
  "Pure translation from Quil key events to game inputs. Must not require Quil
  so acceptance steps and specs can use it directly."
  )

(def ^:private escape-char
  "The raw key Processing reports for the Esc key."
  (char 27))

(def ^:private coded-key-sentinel
  "The raw-key Processing reports for coded keys (arrows, F-keys, etc.)."
  (char 65535))

(defn- by-key-code
  "Translate a coded key by its key-code to a navigation action.
  Quil reports coded keys with this sentinel as raw-key and the actual code here."
  [key-code]
  (case key-code
    38 :up
    40 :down
    37 :left
    39 :right
    36 :home
    35 :end
    33 :page-up
    34 :page-down
    nil))

(defn- by-key
  "Translate events matched by the :key field to navigation actions."
  [key modifiers]
  (case key
    :up :up
    :down :down
    :left :left
    :right :right
    nil))

(def ^:private special-raw-keys
  "Raw keys with a fixed navigation action, keyed by the literal char
  Processing reports for them. Does NOT include the printable ASCII codes
  33 (!) 34 (\") 35 (#) 36 ($) which are real characters, not navigation.
  Tab is handled separately to allow for Shift+Tab."
  {\newline   :confirm
   \return    :confirm
   escape-char :back
   \space     :toggle
   (char 8)   :backspace
   (char 127) :delete})

(defn- printable-char
  "Build a character input for a raw key that isn't one of the special
  actions, attaching modifiers when present. Returns nil for the Processing
  no-char sentinel or a missing raw key."
  [raw-key modifiers]
  (when (and (some? raw-key) (not= raw-key coded-key-sentinel))
    (let [shift-tab? (and (= raw-key \tab) (contains? modifiers :shift))]
      (cond
        shift-tab? :shift-tab
        (seq modifiers) {:char raw-key :mods modifiers}
        :else {:char raw-key}))))

(defn- by-raw-key
  "Translate events matched by the :raw-key field to actions or characters."
  [raw-key modifiers]
  (if (= raw-key \tab)
    (if (contains? modifiers :shift) :shift-tab :tab)
    (or (get special-raw-keys raw-key)
        (printable-char raw-key modifiers))))

(defn escape?
  "Check if an event is a raw Escape key. Returns a boolean."
  [event]
  (= escape-char (:raw-key event)))

(defn event->input
  "Translate a Quil key event map {:key kw :raw-key char :key-code int :modifiers #{...}}
  to a game input (keyword action, character map, or nil)."
  [event]
  (if (nil? event)
    nil
    (let [{:keys [key raw-key key-code modifiers]} event
          modifiers (or modifiers #{})]
      (cond
        ;; Coded key: raw-key is the sentinel, translate by key-code
        (= raw-key coded-key-sentinel) (by-key-code key-code)
        ;; Regular :key field (arrows, etc.)
        (some? key) (let [by-key-result (by-key key modifiers)]
                      (if (some? by-key-result)
                        by-key-result
                        (by-raw-key raw-key modifiers)))
        ;; Fall through to raw-key translation
        :else (by-raw-key raw-key modifiers)))))
