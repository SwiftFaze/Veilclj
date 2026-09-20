(ns veil.ui.input
  "Pure translation from Quil key events to game inputs. Must not require Quil
  so acceptance steps and specs can use it directly."
  )

(def ^:private escape-char
  "The raw key Processing reports for the Esc key."
  (char 27))

(defn- by-key
  "Translate events matched by the :key field to navigation actions."
  [key modifiers]
  (case key
    :up :up
    :down :down
    :left :left
    :right :right
    :tab (if (contains? modifiers :shift) :shift-tab :tab)
    nil))

(defn- by-raw-key
  "Translate events matched by the :raw-key field to actions or characters."
  [raw-key modifiers]
  (cond
    (or (= raw-key \newline) (= raw-key \return)) :confirm
    (= raw-key escape-char) :back
    (= raw-key \space) :toggle
    (= raw-key (char 8)) :backspace
    (= raw-key (char 127)) :delete
    (= raw-key (char 36)) :home
    (= raw-key (char 35)) :end
    (= raw-key (char 33)) :page-up
    (= raw-key (char 34)) :page-down
    :else (when (and (some? raw-key) (not (= raw-key (char 65535))))
            (if (seq modifiers)
              {:char raw-key :mods modifiers}
              {:char raw-key}))))

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
    (let [{:keys [key raw-key modifiers]} event
          modifiers (or modifiers #{})
          by-key-result (by-key key modifiers)]
      (if (some? by-key-result)
        by-key-result
        (by-raw-key raw-key modifiers)))))
