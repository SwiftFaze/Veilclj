(ns veil.ui.widgets-spec
  (:require [speclj.core :refer :all]
            [veil.ui.buffer :as buffer]
            [veil.ui.widgets :as widgets]))

(defn- ascii-row
  "A frame's box-drawing glyphs mapped to ASCII, for readable assertions."
  [buf row]
  (apply str (map (fn [ch]
                     (cond
                       (= ch (char 0x250C)) \+
                       (= ch (char 0x2510)) \+
                       (= ch (char 0x2514)) \+
                       (= ch (char 0x2518)) \+
                       (= ch (char 0x2500)) \-
                       (= ch (char 0x2502)) \|
                       :else ch))
                   (buffer/row-text buf row))))

(describe "frame"
  (it "puts its title into the top edge, inset one cell with a space each side"
    (let [buf (widgets/frame (buffer/blank 20 5) 0 0 20 5 "Gallery" :BORDER :BACKGROUND)]
      (should= "+- Gallery --------+" (ascii-row buf 0))))

  (it "draws its other three edges as an ordinary box"
    (let [buf (widgets/frame (buffer/blank 20 5) 0 0 20 5 "Gallery" :BORDER :BACKGROUND)]
      (should= {:glyph (char 0x2514) :fg :BORDER :bg :BACKGROUND} (buffer/cell buf 0 4))
      (should= {:glyph (char 0x2518) :fg :BORDER :bg :BACKGROUND} (buffer/cell buf 19 4))
      (should= {:glyph (char 0x2502) :fg :BORDER :bg :BACKGROUND} (buffer/cell buf 0 2))))

  (it "cuts a title too long for the frame, with no ellipsis"
    (let [buf (widgets/frame (buffer/blank 20 5) 0 0 13 5 "Gallery" :BORDER :BACKGROUND)]
      (should= "+- Gallery -+" (subs (ascii-row buf 0) 0 13))))

  (it "draws untitled at w <= 6, same as a box"
    (let [with-title (widgets/frame (buffer/blank 20 5) 0 0 6 5 "Gallery" :BORDER :BACKGROUND)
          without-title (buffer/draw-box (buffer/blank 20 5) 0 0 6 5 :BORDER :BACKGROUND)]
      (should= without-title with-title)))

  (it "is the same as a box when given no title"
    (let [with-nil (widgets/frame (buffer/blank 20 5) 0 0 20 5 nil :BORDER :BACKGROUND)
          box (buffer/draw-box (buffer/blank 20 5) 0 0 20 5 :BORDER :BACKGROUND)]
      (should= box with-nil)))

  (it "drops placements that fall outside the buffer"
    (let [buf (widgets/frame (buffer/blank 5 5) 0 0 20 5 "Gallery" :BORDER :BACKGROUND)]
      (should= 5 (:cols buf)))))

(describe "title-bar"
  (it "fills the row in SELECTED_TEXT on SELECTED_HIGHLIGHT"
    (let [buf (widgets/title-bar (buffer/blank 80 24) "VEIL" "Widget Gallery" 0)]
      (should= {:glyph \space :fg :SELECTED_TEXT :bg :SELECTED_HIGHLIGHT} (buffer/cell buf 0 0))
      (should= {:glyph \V :fg :SELECTED_TEXT :bg :SELECTED_HIGHLIGHT} (buffer/cell buf 1 0))))

  (it "centers the center text"
    (let [buf (widgets/title-bar (buffer/blank 80 24) "VEIL" "Widget Gallery" 0)]
      (should= "Widget Gallery" (subs (buffer/row-text buf 0) 33 47)))))

(describe "status-line"
  (it "centers a bracketed message in reverse video"
    (let [buf (widgets/status-line (buffer/blank 80 24) "Activated Guava" 22)
          bracketed "[ Activated Guava ]"]
      (should= bracketed (subs (buffer/row-text buf 22) 30 (+ 30 (count bracketed))))
      (should= {:glyph \[ :fg :SELECTED_TEXT :bg :SELECTED_HIGHLIGHT} (buffer/cell buf 30 22))))

  (it "leaves the row blank with no message"
    (let [blank (buffer/blank 80 24)]
      (should= blank (widgets/status-line blank nil 22))))

  (it "leaves the row blank with an empty message"
    (let [blank (buffer/blank 80 24)]
      (should= blank (widgets/status-line blank "" 22)))))

(describe "keycap-label"
  (it "labels the back action Esc"
    (should= "Esc" (widgets/keycap-label :back)))

  (it "labels the confirm action Enter"
    (should= "Enter" (widgets/keycap-label :confirm)))

  (it "labels the tab action Tab"
    (should= "Tab" (widgets/keycap-label :tab)))

  (it "labels the up action Up"
    (should= "Up" (widgets/keycap-label :up)))

  (it "labels the f12 action F12"
    (should= "F12" (widgets/keycap-label :f12)))

  (it "labels a plain character as its uppercase letter"
    (should= "A" (widgets/keycap-label {:char \a})))

  (it "labels a character with modifiers as ^ plus its uppercase letter"
    (should= "^X" (widgets/keycap-label {:char \x :mods #{:ctrl}})))

  (it "labels anything else as ?"
    (should= "?" (widgets/keycap-label :unknown))
    (should= "?" (widgets/keycap-label nil))))

(describe "keycap"
  (it "writes its label in reverse video"
    (let [buf (widgets/keycap (buffer/blank 10 1) :back 2 0)]
      (should= "Esc" (subs (buffer/row-text buf 0) 2 5))
      (should= {:glyph \E :fg :SELECTED_TEXT :bg :SELECTED_HIGHLIGHT} (buffer/cell buf 2 0)))))

(describe "hint-bar-rows"
  (it "is 0 with no hints"
    (should= 0 (widgets/hint-bar-rows (buffer/blank 40 6) [])))

  (it "is 1 when every hint fits on one row"
    (should= 1 (widgets/hint-bar-rows (buffer/blank 40 6)
                                      [[:back "Back"] [{:char \x :mods #{:ctrl}} "Exit"]])))

  (it "wraps to more rows when hints don't fit on one"
    (should= 3 (widgets/hint-bar-rows (buffer/blank 5 6)
                                      [[:back "Back"] [{:char \x :mods #{:ctrl}} "Exit"]
                                       [{:char \o :mods #{:ctrl}} "Write"]]))))

(describe "hint-bar"
  (it "draws nothing and returns the buffer unchanged with no hints"
    (let [blank (buffer/blank 20 6)]
      (should= blank (widgets/hint-bar blank []))))

  (it "lays hints out in equal slots, docked at the bottom"
    (let [buf (widgets/hint-bar (buffer/blank 40 6)
                                [[:back "Back"] [{:char \x :mods #{:ctrl}} "Exit"]
                                 [{:char \o :mods #{:ctrl}} "Write"]])]
      (should= "Esc" (subs (buffer/row-text buf 5) 0 3))
      (should= "Back" (subs (buffer/row-text buf 5) 4 8))
      (should= "^X" (subs (buffer/row-text buf 5) 9 11))
      (should= {:glyph \B :fg :NORMAL_TEXT :bg :BACKGROUND} (buffer/cell buf 4 5))))

  (it "wraps onto more rows, growing upward, when a hint bar is too narrow for one row"
    (let [buf (widgets/hint-bar (buffer/blank 5 6)
                                [[:back "Back"] [{:char \x :mods #{:ctrl}} "Exit"]
                                 [{:char \o :mods #{:ctrl}} "Write"]])]
      (should= "Esc B" (subs (buffer/row-text buf 3) 0 5))
      (should= "^O Wr" (subs (buffer/row-text buf 5) 0 5)))))

(describe "accelerator-label"
  (it "highlights the first case-insensitive occurrence of the letter in ACCENT"
    (let [buf (widgets/accelerator-label (buffer/blank 20 1) "Apple" \a 0 0 :NORMAL_TEXT :BACKGROUND)]
      (should= {:glyph \A :fg :ACCENT :bg :BACKGROUND} (buffer/cell buf 0 0))))

  (it "highlights a letter that isn't the label's first"
    (let [buf (widgets/accelerator-label (buffer/blank 20 1) "Guava" \u 0 0 :NORMAL_TEXT :BACKGROUND)]
      (should= {:glyph \u :fg :ACCENT :bg :BACKGROUND} (buffer/cell buf 1 0))))

  (it "leaves every other letter in the given fg/bg"
    (let [buf (widgets/accelerator-label (buffer/blank 20 1) "Guava" \u 0 0 :NORMAL_TEXT :BACKGROUND)]
      (should= {:glyph \G :fg :NORMAL_TEXT :bg :BACKGROUND} (buffer/cell buf 0 0))
      (should= {:glyph \a :fg :NORMAL_TEXT :bg :BACKGROUND} (buffer/cell buf 2 0))
      (should= {:glyph \a :fg :NORMAL_TEXT :bg :BACKGROUND} (buffer/cell buf 4 0)))))

(describe "badge"
  (it "writes its text padded with a space on each side, in BACKGROUND on its color"
    (let [buf (widgets/badge (buffer/blank 10 1) :SUCCESS "3" 0 0)]
      (should= " 3 " (subs (buffer/row-text buf 0) 0 3))
      (should= {:glyph \space :fg :BACKGROUND :bg :SUCCESS} (buffer/cell buf 0 0)))))

(describe "chip"
  (it "writes its tag in brackets, in its color on BACKGROUND when not focused"
    (let [buf (widgets/chip (buffer/blank 10 1) :ERROR "fire" 0 0 false)]
      (should= "[fire]" (subs (buffer/row-text buf 0) 0 6))
      (should= {:glyph \[ :fg :ERROR :bg :BACKGROUND} (buffer/cell buf 0 0))))

  (it "swaps its color onto its background when focused"
    (let [buf (widgets/chip (buffer/blank 10 1) :ERROR "fire" 0 0 true)]
      (should= {:glyph \[ :fg :BACKGROUND :bg :ERROR} (buffer/cell buf 0 0)))))
