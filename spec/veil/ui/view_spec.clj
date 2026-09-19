(ns veil.ui.view-spec
  (:require [speclj.core :refer :all]
            [veil.ui.view :as view]
            [veil.ui.buffer :as buffer]
            [veil.game.state :as state]))

(defn- themed-state
  "Build a state with the default theme loaded."
  []
  (state/starting
    {:load-order ["core"] :content {}}
    {"core:default" {:SELECTED_HIGHLIGHT [192 192 192]
                      :SELECTED_TEXT [0 0 0]
                      :NORMAL_TEXT [255 255 255]
                      :DIMMED_TEXT [128 128 128]
                      :BACKGROUND [0 0 0]
                      :INVALID_HIGHLIGHT [224 90 78]
                      :VALID_HIGHLIGHT [111 207 125]
                      :TABLE_HEADER_BACKGROUND [26 26 26]
                      :BORDER [192 192 192]
                      :SCROLLBAR_THUMB [128 128 128]
                      :ACCENT [238 179 146]
                      :WINDOW_BORDER [255 255 255]
                      :TABLE_HEADER_TEXT [0 194 194]
                      :SUCCESS [111 207 125]
                      :ERROR [224 90 78]
                      :WARNING [238 179 146]
                      :INFO [238 179 146]
                      :FOCUSED_BORDER [238 179 146]
                      :SHADOW [0 0 0]}}))

(describe "main menu buffer"
  (it "includes VEIL title"
    (let [buf (view/buffer (themed-state) 80 24)]
      (should (some #(= "VEIL" (apply str %)) (map (fn [r] (filter #(not= \space %) (map :glyph r))) (:cells buf))))))

  (it "includes menu items"
    (let [buf (view/buffer (themed-state) 80 24)]
      (should= 80 (:cols buf))
      (should= 24 (:rows buf))))

  (it "marks the selected item in reverse video"
    (let [s (themed-state)
          buf (view/buffer s 80 24)
          selected (state/selected-item s)
          selected-row (+ 4)]
      (should (some #(= :SELECTED_HIGHLIGHT (:bg %)) (get-in buf [:cells selected-row]))))))
