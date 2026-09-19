(ns veil.ui.buffer-spec
  (:require [speclj.core :refer :all]
            [veil.ui.buffer :as buffer]))

(describe "blank-cell"
  (it "is a space in NORMAL_TEXT on BACKGROUND"
    (should= {:glyph \space :fg :NORMAL_TEXT :bg :BACKGROUND}
             buffer/blank-cell)))

(describe "blank"
  (it "creates a buffer of the requested size"
    (let [b (buffer/blank 80 24)]
      (should= 80 (:cols b))
      (should= 24 (:rows b))))

  (it "fills with blank cells"
    (let [b (buffer/blank 3 2)]
      (should= buffer/blank-cell (buffer/cell b 0 0))
      (should= buffer/blank-cell (buffer/cell b 2 1)))))

(describe "cell"
  (it "returns the cell at a position"
    (let [b (buffer/blank 3 2)]
      (should= buffer/blank-cell (buffer/cell b 1 0))))

  (it "returns nil for out-of-range positions"
    (let [b (buffer/blank 3 2)]
      (should-be-nil (buffer/cell b 3 0))
      (should-be-nil (buffer/cell b 0 2))
      (should-be-nil (buffer/cell b -1 0))
      (should-be-nil (buffer/cell b 0 -1)))))

(describe "row-text"
  (it "returns the glyphs in a row as a string"
    (let [b (buffer/blank 5 1)
          b (buffer/write-text b 0 0 "abcde" :NORMAL_TEXT :BACKGROUND)]
      (should= "abcde" (buffer/row-text b 0))))

  (it "returns empty string for a row out of range"
    (let [b (buffer/blank 3 1)]
      (should= "" (buffer/row-text b 1))
      (should= "" (buffer/row-text b -1)))))

(describe "write-text"
  (it "writes text into the buffer"
    (let [b (buffer/blank 10 3)
          b (buffer/write-text b 2 1 "Hi!" :ACCENT :SELECTED_HIGHLIGHT)]
      (should= "  Hi!     " (buffer/row-text b 1))))

  (it "clips text past the right edge"
    (let [b (buffer/blank 10 3)
          b (buffer/write-text b 8 1 "Hello" :NORMAL_TEXT :BACKGROUND)]
      (should= "        He" (buffer/row-text b 1))))

  (it "handles text starting left of the left edge (negative column)"
    (let [b (buffer/blank 10 3)
          b (buffer/write-text b -2 0 "Hello" :NORMAL_TEXT :BACKGROUND)]
      (should= "llo       " (buffer/row-text b 0))))

  (it "does not change anything when text is entirely outside the grid (column past right)"
    (let [original (buffer/blank 10 3)
          b (buffer/write-text original 10 0 "Hi" :ERROR :BACKGROUND)]
      (should= original b)))

  (it "does not change anything when text is entirely outside the grid (row past bottom)"
    (let [original (buffer/blank 10 3)
          b (buffer/write-text original 0 3 "Hi" :ERROR :BACKGROUND)]
      (should= original b)))

  (it "does not change anything when text is entirely outside the grid (row negative)"
    (let [original (buffer/blank 10 3)
          b (buffer/write-text original 0 -1 "Hi" :ERROR :BACKGROUND)]
      (should= original b)))

  (it "does not mutate the original buffer"
    (let [original (buffer/blank 10 3)]
      (buffer/write-text original 0 0 "Hi" :ERROR :BACKGROUND)
      (should= original (buffer/blank 10 3)))))

(describe "fill-rect"
  (it "fills a rectangle with a color"
    (let [b (buffer/blank 10 5)
          b (buffer/fill-rect b 2 1 3 2 :SELECTED_HIGHLIGHT)]
      (should= "          " (buffer/row-text b 1))
      (should= "          " (buffer/row-text b 2))))

  (it "replaces glyphs that were in the rectangle"
    (let [b (buffer/blank 10 1)
          b (buffer/write-text b 0 0 "abcdef" :NORMAL_TEXT :BACKGROUND)
          b (buffer/fill-rect b 1 0 2 1 :ACCENT)]
      (should= "a  def    " (buffer/row-text b 0))))

  (it "clips rectangles to the grid edge"
    (let [b (buffer/blank 10 5)
          b (buffer/fill-rect b 8 3 5 5 :ACCENT)]
      (should= "          " (buffer/row-text b 4))))

  (it "does nothing for zero width"
    (let [original (buffer/blank 10 5)
          b (buffer/fill-rect original 2 1 0 2 :ACCENT)]
      (should= original b)))

  (it "does nothing for zero height"
    (let [original (buffer/blank 10 5)
          b (buffer/fill-rect original 2 1 3 0 :ACCENT)]
      (should= original b)))

  (it "does nothing for negative width"
    (let [original (buffer/blank 10 5)
          b (buffer/fill-rect original 2 1 -1 2 :ACCENT)]
      (should= original b)))

  (it "does nothing for negative height"
    (let [original (buffer/blank 10 5)
          b (buffer/fill-rect original 2 1 3 -1 :ACCENT)]
      (should= original b))))

(describe "draw-box"
  (it "draws corners"
    (let [b (buffer/blank 10 6)
          b (buffer/draw-box b 1 1 5 4 :BORDER :BACKGROUND)]
      (should= (char 0x250C) (:glyph (buffer/cell b 1 1)))
      (should= (char 0x2510) (:glyph (buffer/cell b 5 1)))
      (should= (char 0x2514) (:glyph (buffer/cell b 1 4)))
      (should= (char 0x2518) (:glyph (buffer/cell b 5 4)))))

  (it "draws edges"
    (let [b (buffer/blank 10 6)
          b (buffer/draw-box b 1 1 5 4 :BORDER :BACKGROUND)]
      (should= (char 0x2500) (:glyph (buffer/cell b 3 1)))
      (should= (char 0x2500) (:glyph (buffer/cell b 3 4)))
      (should= (char 0x2502) (:glyph (buffer/cell b 1 2)))
      (should= (char 0x2502) (:glyph (buffer/cell b 5 3)))))

  (it "leaves interior and outside untouched"
    (let [b (buffer/blank 10 6)
          b (buffer/write-text b 2 2 "xyz" :NORMAL_TEXT :BACKGROUND)
          b (buffer/draw-box b 1 1 5 4 :BORDER :BACKGROUND)]
      (should= \x (:glyph (buffer/cell b 2 2)))
      (should= \space (:glyph (buffer/cell b 3 3)))
      (should= \space (:glyph (buffer/cell b 0 1)))))

  (it "does nothing for width < 2"
    (let [original (buffer/blank 10 6)
          b (buffer/draw-box original 1 1 1 4 :BORDER :BACKGROUND)]
      (should= original b)))

  (it "does nothing for height < 2"
    (let [original (buffer/blank 10 6)
          b (buffer/draw-box original 1 1 5 1 :BORDER :BACKGROUND)]
      (should= original b)))

  (it "clips to the grid edge"
    (let [b (buffer/blank 10 6)
          b (buffer/draw-box b 7 3 6 5 :BORDER :BACKGROUND)]
      (should= (char 0x250C) (:glyph (buffer/cell b 7 3)))
      (should= (char 0x2500) (:glyph (buffer/cell b 9 3)))
      (should= (char 0x2502) (:glyph (buffer/cell b 7 5))))))
