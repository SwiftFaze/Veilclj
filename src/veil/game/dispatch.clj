(ns veil.game.dispatch
  "Focus-first input dispatch chain. Handlers consume or pass input; the first
  handler to consume wins. Nothing in the chain, fall through to screen bindings.")

(defn dispatch
  "Walk a chain of handlers, offering each input in order. A handler is
  (fn [state input] -> state-or-nil). Nil means 'not consumed, keep going'.
  Any returned state means consumed, walk stops, and that state is returned.
  If nothing in the chain consumes, fall through to screen-bindings.

  Returns a tuple [state consumed?] so the caller can decide the next step."
  [state input handlers screen-bindings]
  (if (seq handlers)
    (loop [s state, remaining handlers]
      (if (seq remaining)
        (let [result ((first remaining) s input)]
          (if (some? result)
            [result true]
            (recur s (rest remaining))))
        [s false]))
    [state false]))
