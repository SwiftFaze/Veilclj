(ns veil.game.state-property-spec
  "Property tests for veil.game.state invariants.

   Invariant: Menu selection is always a valid index into the current menu's items,
   regardless of any sequence of game inputs applied to the initial state."
  (:require [speclj.core :refer :all]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [veil.game.state :as state]
            [veil.property-support :as support]))

;; Game inputs: the vocabulary passed to handle-input
(def game-inputs
  (gen/elements [:up :down :confirm :back nil]))

(def menu-navigation-property
  "Property: applying any sequence of game inputs to the initial state
   always leaves :selected as a valid index into the menu's items."
  (prop/for-all [inputs (gen/vector game-inputs)]
    (let [s (reduce state/handle-input (state/initial) inputs)
          items (state/menu-items s)
          selected-idx (get-in s [:menu :selected])]
      (and
        ;; :selected is a non-negative integer
        (integer? selected-idx)
        (>= selected-idx 0)
        ;; :selected is within bounds
        (< selected-idx (count items))
        ;; selected-item returns a real item from the menu
        (contains? (set items) (state/selected-item s))))))

(describe "menu navigation invariant"
  (it "holds across all input sequences"
    (should-be-nil (support/run menu-navigation-property))))
