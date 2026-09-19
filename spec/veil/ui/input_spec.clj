(ns veil.ui.input-spec
  (:require [speclj.core :refer :all]
            [veil.ui.input :as input]))

(describe "arrow up"
  (it "translates Up arrow to :up"
    (should= :up (input/event->input {:key :up :key-code 38 :raw-key (char 65535)}))))

(describe "arrow down"
  (it "translates Down arrow to :down"
    (should= :down (input/event->input {:key :down :key-code 40 :raw-key (char 65535)}))))

(describe "arrow left"
  (it "translates Left arrow to nil (ignored)"
    (should= nil (input/event->input {:key :left :key-code 37 :raw-key (char 65535)}))))

(describe "arrow right"
  (it "translates Right arrow to nil (ignored)"
    (should= nil (input/event->input {:key :right :key-code 39 :raw-key (char 65535)}))))

(describe "letter s and S"
  (it "translates s and S (case-insensitive) to :down"
    (should= :down (input/event->input {:key :s :raw-key \s}))
    (should= :down (input/event->input {:key :s :raw-key \S}))))

(describe "letter w and W"
  (it "translates w and W (case-insensitive) to :up"
    (should= :up (input/event->input {:key :w :raw-key \w}))
    (should= :up (input/event->input {:key :w :raw-key \W}))))

(describe "letter x"
  (it "translates X key to nil (ignored)"
    (should= nil (input/event->input {:key :x :raw-key \x})))

  (it "translates uppercase X to nil (ignored)"
    (should= nil (input/event->input {:key :x :raw-key \X}))))

(describe "enter key"
  (it "translates Enter (newline char) to :confirm"
    (should= :confirm (input/event->input {:raw-key \newline})))

  (it "translates Enter (return char) to :confirm"
    (should= :confirm (input/event->input {:raw-key \return}))))

(describe "escape key"
  (it "translates Escape to :back"
    (should= :back (input/event->input {:raw-key (char 27)}))))

(describe "space key"
  (it "translates Space to nil (ignored)"
    (should= nil (input/event->input {:key :space :raw-key \space}))))

(describe "unknown and nil"
  (it "returns nil for nil input"
    (should= nil (input/event->input nil)))

  (it "returns nil for unknown keys"
    (should= nil (input/event->input {:key :unknown :raw-key \?}))))

(describe "escape?"
  (it "returns true for Escape raw key"
    (should= true (input/escape? {:raw-key (char 27)})))

  (it "returns false for other keys"
    (should= false (input/escape? {:raw-key \a}))
    (should= false (input/escape? {:raw-key \newline}))
    (should= false (input/escape? {:key :up})))

  (it "returns false for nil event"
    (should= false (input/escape? nil)))

  (it "returns false when raw-key is missing"
    (should= false (input/escape? {:key :up}))))
