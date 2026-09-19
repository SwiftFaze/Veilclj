(ns veil.ui.input
  "Pure translation from Quil key events to game inputs. Must not require Quil
  so acceptance steps and specs can use it directly."
  )

(def ^:private escape-char
  "The raw key Processing reports for the Esc key."
  (char 27))

(defn- by-key
  "Translate events matched by the :key field."
  [key]
  (case key
    :up :up
    :down :down
    :s :down
    :w :up
    nil))

(defn- by-raw-key
  "Translate events matched by the :raw-key field."
  [raw-key]
  (cond
    (or (= raw-key \newline) (= raw-key \return)) :confirm
    (= raw-key escape-char) :back
    :else nil))

(defn escape?
  "Check if an event is a raw Escape key. Returns a boolean."
  [event]
  (= escape-char (:raw-key event)))

(defn event->input
  "Translate a Quil key event map {:key kw :raw-key char :key-code int} to a
  game input (:up, :down, :confirm, :back) or nil."
  [event]
  (if (nil? event)
    nil
    (let [{:keys [key raw-key]} event
          by-key-result (by-key key)]
      (if (some? by-key-result)
        by-key-result
        (by-raw-key raw-key)))))
