(ns veil.acceptance.step-support
  "Result constructors shared by every step-handler namespace.")

(defn ok []
  {:ok? true})

(defn fail [message]
  {:ok? false :message message})

(defn check [passed? message]
  (if passed? (ok) (fail message)))

(defn arrange
  "Store the given world entries; the step itself always succeeds."
  [world & entries]
  (apply swap! world assoc entries)
  (ok))
