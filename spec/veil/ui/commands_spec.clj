(ns veil.ui.commands-spec
  (:require [speclj.core :refer :all]
            [veil.ui.commands :as commands]
            [veil.ui.buffer :as buffer]
            [veil.game.state :as state]))

(defn- test-state
  "A game state with the default theme."
  []
  (state/starting
    {:load-order ["core"] :content {}}
    {"core:default" {:BACKGROUND [5 5 5]
                     :NORMAL_TEXT [255 255 255]
                     :SELECTED_HIGHLIGHT [192 192 192]
                     :SELECTED_TEXT [0 0 0]
                     :BORDER [128 128 128]
                     :ACCENT [238 179 146]
                     :DIMMED_TEXT [128 128 128]
                     :INVALID_HIGHLIGHT [200 50 50]
                     :VALID_HIGHLIGHT [50 200 50]
                     :TABLE_HEADER_BACKGROUND [50 50 100]
                     :SCROLLBAR_THUMB [100 100 100]
                     :WINDOW_BORDER [200 200 200]
                     :TABLE_HEADER_TEXT [200 200 200]
                     :SUCCESS [50 200 50]
                     :ERROR [200 50 50]
                     :WARNING [238 179 146]
                     :INFO [238 179 146]
                     :FOCUSED_BORDER [238 179 146]
                     :SHADOW [5 5 5]}}))

(describe "frame"
  (it "includes the background color from the theme"
    (let [state (test-state)
          buf (buffer/blank 80 24)
          frame (commands/frame state buf 12 25)]
      (should= [5 5 5] (:background frame))))

  (it "creates no rects or glyphs for a blank buffer"
    (let [state (test-state)
          buf (buffer/blank 80 24)
          frame (commands/frame state buf 12 25)]
      (should= [] (:rects frame))
      (should= [] (:glyphs frame))))

  (it "creates a rect for a cell with non-BACKGROUND background"
    (let [state (test-state)
          buf (buffer/blank 80 24)
          buf (buffer/fill-rect buf 3 2 1 1 :ACCENT)
          frame (commands/frame state buf 12 25)]
      (should= 1 (count (:rects frame)))
      (should= {:x 36 :y 50 :w 12 :h 25 :color [238 179 146]}
               (first (:rects frame)))))

  (it "creates a glyph command for a non-space glyph"
    (let [state (test-state)
          buf (buffer/blank 80 24)
          buf (buffer/write-text buf 3 2 "A" :BORDER :BACKGROUND)
          frame (commands/frame state buf 12 25)]
      (should= 1 (count (:glyphs frame)))
      (should= {:text "A" :x 36 :y 50 :color [128 128 128]}
               (first (:glyphs frame)))))

  (it "does not create a glyph command for a space glyph"
    (let [state (test-state)
          buf (buffer/blank 80 24)
          buf (buffer/fill-rect buf 3 2 1 1 :ACCENT)
          frame (commands/frame state buf 12 25)]
      (should= 1 (count (:rects frame)))
      (should= [] (:glyphs frame))))

  (it "creates both rect and glyph for a colored cell with a glyph"
    (let [state (test-state)
          buf (buffer/blank 80 24)
          buf (buffer/write-text buf 3 2 "Q" :SELECTED_TEXT :SELECTED_HIGHLIGHT)
          frame (commands/frame state buf 12 25)]
      (should= 1 (count (:rects frame)))
      (should= 1 (count (:glyphs frame)))
      (should= {:x 36 :y 50 :w 12 :h 25 :color [192 192 192]}
               (first (:rects frame)))
      (should= {:text "Q" :x 36 :y 50 :color [0 0 0]}
               (first (:glyphs frame))))))
