(ns veil.ui.input
  "Pure translation from Quil key events to game inputs. Must not require Quil
  so acceptance steps and specs can use it directly."
  )

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
    (= raw-key (char 27)) :back
    :else nil))

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
