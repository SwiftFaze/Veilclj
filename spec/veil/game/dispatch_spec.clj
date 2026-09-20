(ns veil.game.dispatch-spec
  (:require [speclj.core :refer :all]
            [veil.game.dispatch :as dispatch]))

(describe "dispatch"
  (it "returns the state unchanged and consumed=false when chain is empty"
    (let [s {:screen :main-menu}
          [result consumed?] (dispatch/dispatch s :down [] nil)]
      (should= s result)
      (should= false consumed?)))

  (it "calls the first handler with state and input"
    (let [handler (fn [state input] (when (= input :down) (assoc state :moved true)))
          s {:screen :main-menu}
          [result consumed?] (dispatch/dispatch s :down [handler] nil)]
      (should= true (:moved result))
      (should= true consumed?)))

  (it "stops at the first handler that consumes"
    (let [handler1 (fn [s i] (assoc s :handler 1))
          handler2 (fn [s i] (assoc s :handler 2))
          s {:screen :main-menu}
          [result consumed?] (dispatch/dispatch s :down [handler1 handler2] nil)]
      (should= 1 (:handler result))
      (should= true consumed?)))

  (it "continues to next handler if current one returns nil"
    (let [handler1 (fn [s i] nil)
          handler2 (fn [s i] (assoc s :handler 2))
          s {:screen :main-menu}
          [result consumed?] (dispatch/dispatch s :down [handler1 handler2] nil)]
      (should= 2 (:handler result))
      (should= true consumed?)))

  (it "returns consumed?=false and unchanged state when no handler consumes"
    (let [handler1 (fn [s i] nil)
          handler2 (fn [s i] nil)
          s {:screen :main-menu}
          [result consumed?] (dispatch/dispatch s :down [handler1 handler2] nil)]
      (should= s result)
      (should= false consumed?))))
