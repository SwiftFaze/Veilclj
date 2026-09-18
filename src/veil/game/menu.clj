(ns veil.game.menu
  "Main menu state: items and selection."
  )

(defn new-menu
  "Create a new menu with the standard items, New Game selected."
  []
  {:items ["New Game" "Options" "Quit"]
   :selected 0})

(defn move
  "Move selection up or down (direction :up or :down), wrapping at edges."
  [menu direction]
  (case direction
    :down (update menu :selected #(mod (inc %) (count (:items menu))))
    :up (update menu :selected #(mod (dec %) (count (:items menu))))
    menu))

(defn selected-item
  "Get the label of the currently selected menu item."
  [menu]
  (get (:items menu) (:selected menu)))
