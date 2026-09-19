(ns veil.ui.qa.script-spec
  (:require [speclj.core :refer :all]
            [veil.ui.qa.script :as script]))

(describe "parse"
  (it "parses a single Down key"
    (let [result (script/parse "Down")]
      (should-be-nil (:error result))
      (should= [{:tick 1 :key "Down"}] (:steps result))))

  (it "parses multiple keys with consecutive ticks"
    (let [result (script/parse "Down\nEnter")]
      (should-be-nil (:error result))
      (should= [{:tick 1 :key "Down"} {:tick 2 :key "Enter"}] (:steps result))))

  (it "parses a wait that skips ticks"
    (let [result (script/parse "Down\nwait 3\nEnter")]
      (should-be-nil (:error result))
      (should= [{:tick 1 :key "Down"} {:tick 5 :key "Enter"}] (:steps result))))

  (it "parses wait 0 as no skip"
    (let [result (script/parse "Down\nwait 0\nEnter")]
      (should-be-nil (:error result))
      (should= [{:tick 1 :key "Down"} {:tick 2 :key "Enter"}] (:steps result))))

  (it "skips blank lines"
    (let [result (script/parse "Down\n\nEnter")]
      (should-be-nil (:error result))
      (should= [{:tick 1 :key "Down"} {:tick 2 :key "Enter"}] (:steps result))))

  (it "removes comments after #"
    (let [result (script/parse "Down # move down\nEnter")]
      (should-be-nil (:error result))
      (should= [{:tick 1 :key "Down"} {:tick 2 :key "Enter"}] (:steps result))))

  (it "parses single letter keys"
    (let [result (script/parse "W")]
      (should-be-nil (:error result))
      (should= [{:tick 1 :key "W"}] (:steps result))))

  (it "parses digit keys"
    (let [result (script/parse "7")]
      (should-be-nil (:error result))
      (should= [{:tick 1 :key "7"}] (:steps result))))

  (it "rejects unknown key"
    (let [result (script/parse "Bogus")]
      (should= "line 1: unknown key \"Bogus\"" (:error result))
      (should-be-nil (:steps result))))

  (it "rejects wait without number"
    (let [result (script/parse "wait")]
      (should= "line 1: wait needs a whole number" (:error result))
      (should-be-nil (:steps result))))

  (it "rejects wait with non-integer"
    (let [result (script/parse "wait 1.5")]
      (should= "line 1: wait needs a whole number" (:error result))
      (should-be-nil (:steps result))))

  (it "rejects wait with negative number"
    (let [result (script/parse "wait -1")]
      (should= "line 1: wait needs a whole number" (:error result))
      (should-be-nil (:steps result))))

  (it "reports first error only"
    (let [result (script/parse "Down\nBogus\nHyper")]
      (should= "line 2: unknown key \"Bogus\"" (:error result))
      (should-be-nil (:steps result))))

  (it "parses empty script as no keys"
    (let [result (script/parse "")]
      (should-be-nil (:error result))
      (should= [] (:steps result))))

  (it "parses blank script as no keys"
    (let [result (script/parse "  ")]
      (should-be-nil (:error result))
      (should= [] (:steps result)))))

(describe "key->event"
  (it "builds Down event"
    (let [evt (script/key->event "Down")]
      (should= :down (:key evt))
      (should= 40 (:key-code evt))))

  (it "builds Up event"
    (let [evt (script/key->event "Up")]
      (should= :up (:key evt))
      (should= 38 (:key-code evt))))

  (it "builds Left event"
    (let [evt (script/key->event "Left")]
      (should= :left (:key evt))
      (should= 37 (:key-code evt))))

  (it "builds Right event"
    (let [evt (script/key->event "Right")]
      (should= :right (:key evt))
      (should= 39 (:key-code evt))))

  (it "builds Enter event"
    (let [evt (script/key->event "Enter")]
      (should= \newline (:raw-key evt))))

  (it "builds Esc event"
    (let [evt (script/key->event "Esc")]
      (should= (char 27) (:raw-key evt))))

  (it "builds Space event"
    (let [evt (script/key->event "Space")]
      (should= :space (:key evt))
      (should= \space (:raw-key evt))))

  (it "builds letter event"
    (let [evt (script/key->event "S")]
      (should= :s (:key evt))
      (should= \S (:raw-key evt))))

  (it "builds digit event"
    (let [evt (script/key->event "7")]
      (should= :7 (:key evt))
      (should= \7 (:raw-key evt)))))

(describe "key-keyword"
  (it "converts Down to :down"
    (should= :down (script/key-keyword "Down")))

  (it "converts Up to :up"
    (should= :up (script/key-keyword "Up")))

  (it "converts Enter to :enter"
    (should= :enter (script/key-keyword "Enter")))

  (it "converts Esc to :esc"
    (should= :esc (script/key-keyword "Esc")))

  (it "converts letter to lowercase keyword"
    (should= :s (script/key-keyword "S")))

  (it "converts digit to keyword"
    (should= :7 (script/key-keyword "7"))))

(describe "event->key-keyword"
  (it "converts newline raw-key to :enter"
    (should= :enter (script/event->key-keyword {:raw-key \newline})))

  (it "converts return raw-key to :enter"
    (should= :enter (script/event->key-keyword {:raw-key \return})))

  (it "converts ESC raw-key to :esc"
    (should= :esc (script/event->key-keyword {:raw-key (char 27)})))

  (it "uses the event's :key if it's a keyword"
    (should= :down (script/event->key-keyword {:key :down :raw-key (char 65535)})))

  (it "converts unknown key to :unknown"
    (should= :unknown (script/event->key-keyword {:raw-key \x})))

  (it "handles letter key"
    (should= :s (script/event->key-keyword {:key :s :raw-key \S})))

  (it "handles digit key"
    (should= :7 (script/event->key-keyword {:key :7 :raw-key \7}))))

(describe "key->event for every named key"
  (it "carries the raw-key Processing reports for each"
    (should= [(char 65535) (char 65535) (char 65535) (char 65535) \newline (char 27) \space]
             (mapv (comp :raw-key script/key->event)
                   ["Down" "Up" "Left" "Right" "Enter" "Esc" "Space"])))

  (it "round-trips every valid key name to the keyword a live press logs"
    (doseq [name ["Down" "Up" "Left" "Right" "Enter" "Esc" "Space" "S" "7"]]
      (should= (script/key-keyword name)
               (script/event->key-keyword (script/key->event name))))))

(describe "parse layout"
  (it "counts a wait on the first line before any key"
    (should= [{:tick 3 :key "Down"}] (:steps (script/parse "wait 2\nDown"))))

  (it "reads CRLF scripts"
    (should= [{:tick 1 :key "Down"} {:tick 2 :key "Enter"}]
             (:steps (script/parse "Down\r\nEnter\r\n"))))

  (it "accepts a trailing comment after a wait"
    (should= [{:tick 4 :key "Down"}] (:steps (script/parse "wait 3 # let it settle\nDown")))))
