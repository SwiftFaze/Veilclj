(ns veil.game.state-spec
  (:require [speclj.core :refer :all]
            [veil.game.state :as state]
            [veil.game.theme :as theme]))

(describe "initial"
  (it "has no themes loaded"
    (should= {} (:themes (state/initial))))

  (it "has the default theme active"
    (should= theme/default-id (theme/active-id (state/initial))))

  (it "starts on the main menu"
    (should= :main-menu (state/screen (state/initial))))

  (it "has the menu in initial state"
    (let [s (state/initial)]
      (should= ["New Game" "Options" "Quit"] (state/menu-items s))))

  (it "starts with New Game selected"
    (let [s (state/initial)]
      (should= "New Game" (state/selected-item s))))

  (it "is not over initially"
    (should= false (state/over? (state/initial))))

  (it "has a player with @ glyph"
    (should= \@ (state/player-glyph (state/initial)))))

(describe "screen"
  (it "returns the current screen keyword"
    (let [s (state/initial)]
      (should= :main-menu (state/screen s)))))

(describe "menu-items"
  (it "returns the menu items for the current state"
    (let [s (state/initial)]
      (should= ["New Game" "Options" "Quit"] (state/menu-items s)))))

(describe "selected-item"
  (it "returns the selected menu item"
    (let [s (state/initial)]
      (should= "New Game" (state/selected-item s)))))

(describe "player-glyph"
  (it "returns the player character"
    (should= \@ (state/player-glyph (state/initial)))))

(describe "over?"
  (it "returns false when game is not over"
    (should= false (state/over? (state/initial))))

  (it "returns true when game is over"
    (let [s (state/initial)]
      (should= true (state/over? (assoc s :over? true))))))

(describe "select-menu-item"
  (it "transitions to map for New Game"
    (let [s (state/initial)]
      (should= :map (state/screen (state/select-menu-item s)))))

  (it "transitions to options for Options"
    (let [s (-> (state/initial) (state/handle-input :down))]
      (should= :options (state/screen (state/select-menu-item s)))))

  (it "sets over? true for Quit"
    (let [s (-> (state/initial) (state/handle-input :down) (state/handle-input :down))]
      (should= true (state/over? (state/select-menu-item s))))))

(describe "handle-main-menu"
  (it "moves down on :down"
    (let [s (state/initial)]
      (should= "Options" (state/selected-item (state/handle-main-menu s :down)))))

  (it "moves up on :up"
    (let [s (-> (state/initial) (state/handle-input :down))]
      (should= "New Game" (state/selected-item (state/handle-main-menu s :up)))))

  (it "selects item on :confirm"
    (let [s (state/initial)]
      (should= :map (state/screen (state/handle-main-menu s :confirm)))))

  (it "ignores other inputs"
    (let [s (state/initial)]
      (should= s (state/handle-main-menu s :back)))))

(describe "handle-map"
  (it "returns to menu on :back"
    (let [s (-> (state/initial) (state/handle-input :confirm))]
      (should= :main-menu (state/screen (state/handle-map s :back)))))

  (it "ignores other inputs"
    (let [s (-> (state/initial) (state/handle-input :confirm))]
      (should= s (state/handle-map s :down)))))

(describe "handle-options"
  (it "returns to menu on :back"
    (let [s (-> (state/initial) (state/handle-input :down) (state/handle-input :confirm))]
      (should= :main-menu (state/screen (state/handle-options s :back)))))

  (it "ignores other inputs"
    (let [s (-> (state/initial) (state/handle-input :down) (state/handle-input :confirm))]
      (should= s (state/handle-options s :down)))))

(describe "handle-input"
  (it "moves menu down on :down input on main menu"
    (let [s (state/initial)]
      (should= "Options" (state/selected-item (state/handle-input s :down)))))

  (it "moves menu up on :up input on main menu"
    (let [s (-> (state/initial) (state/handle-input :down) (state/handle-input :down))]
      (should= "Options" (state/selected-item (state/handle-input s :up)))))

  (it "selects New Game when :confirm on New Game"
    (let [s (state/initial)]
      (should= :map (state/screen (state/handle-input s :confirm)))))

  (it "selects Options when :confirm on Options"
    (let [s (-> (state/initial) (state/handle-input :down))]
      (should= :options (state/screen (state/handle-input s :confirm)))))

  (it "ends the game when :confirm on Quit"
    (let [s (-> (state/initial) (state/handle-input :down) (state/handle-input :down))]
      (should= true (state/over? (state/handle-input s :confirm)))))

  (it "returns to main menu with :back from map, keeping selection"
    (let [s (-> (state/initial) (state/handle-input :down) (state/handle-input :confirm))]
      (let [after-back (state/handle-input s :back)]
        (should= :main-menu (state/screen after-back))
        (should= "Options" (state/selected-item after-back)))))

  (it "returns to main menu with :back from options, keeping selection"
    (let [s (-> (state/initial) (state/handle-input :down) (state/handle-input :confirm))]
      (let [after-back (state/handle-input s :back)]
        (should= :main-menu (state/screen after-back))
        (should= "Options" (state/selected-item after-back)))))

  (it "does nothing on :back when on main menu"
    (let [s (-> (state/initial) (state/handle-input :down))]
      (should= s (state/handle-input s :back))))

  (it "ignores nil input"
    (let [s (state/initial)]
      (should= s (state/handle-input s nil))))

  (it "ignores unknown input"
    (let [s (state/initial)]
      (should= s (state/handle-input s :unknown)))))

(describe "with-mods"
  (it "keeps the mod registry in the state"
    (let [registry {:load-order ["core"] :content {}}]
      (should= registry (state/mods (state/with-mods (state/initial) registry))))))

(describe "mods"
  (it "is nil before a registry is attached"
    (should-be-nil (state/mods (state/initial)))))

(describe "starting"
  (it "returns the initial game state with the mods registry attached"
    (let [registry {:load-order ["core"] :content {}}
          s (state/starting registry)]
      (should= :main-menu (state/screen s))
      (should= registry (state/mods s))))

  (it "has New Game selected"
    (let [registry {:load-order ["core"] :content {}}
          s (state/starting registry)]
      (should= "New Game" (state/selected-item s))))

  (it "is not over"
    (let [registry {:load-order ["core"] :content {}}
          s (state/starting registry)]
      (should-not (state/over? s))))

  (it "carries the themes it is given"
    (let [themes {"core:default" {:BACKGROUND [0 0 0]}}
          s (state/starting {:load-order ["core"] :content {}} themes)]
      (should= themes (:themes s))))

  (it "activates the default theme"
    (let [themes {"core:default" {:BACKGROUND [0 0 0]}}
          s (state/starting {:load-order ["core"] :content {}} themes)]
      (should= theme/default-id (theme/active-id s))))

  (it "carries no themes when none are given"
    (let [s (state/starting {:load-order ["core"] :content {}})]
      (should= {} (:themes s)))))

(describe "with-themes"
  (it "attaches the themes to the state"
    (let [themes {"core:default" {:BACKGROUND [0 0 0]}}]
      (should= themes (:themes (state/with-themes (state/initial) themes)))))

  (it "makes the default theme the active one, replacing any other"
    (let [themes {"core:default" {:BACKGROUND [0 0 0]} "other:dark" {:BACKGROUND [9 9 9]}}
          s (-> (state/initial) (assoc :active-theme "other:dark"))]
      (should= theme/default-id (theme/active-id (state/with-themes s themes))))))
