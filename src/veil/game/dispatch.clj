(ns veil.game.dispatch
  "Focus-first input dispatch chain. Handlers consume or pass input; the first
  handler to consume wins. Caller owns the fall-through to screen bindings.")

(defn dispatch
  "Walk a chain of handlers, offering each input in order. A handler is
  (fn [state input] -> state-or-nil). Nil means 'not consumed, keep going'.
  Any returned state means consumed, walk stops, and that state is returned.

  Returns a tuple [state consumed?] so the caller can decide fall-through."
  [state input handlers]
  (if (seq handlers)
    (loop [s state, remaining handlers]
      (if (seq remaining)
        (let [result ((first remaining) s input)]
          (if (some? result)
            [result true]
            (recur s (rest remaining))))
        [s false]))
    [state false]))
