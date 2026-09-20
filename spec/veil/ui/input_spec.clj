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
  (it "translates Left arrow to :left"
    (should= :left (input/event->input {:key :left :key-code 37 :raw-key (char 65535)}))))

(describe "arrow right"
  (it "translates Right arrow to :right"
    (should= :right (input/event->input {:key :right :key-code 39 :raw-key (char 65535)}))))

(describe "letter s and S"
  (it "translates s to character input"
    (should= {:char \s} (input/event->input {:key :s :raw-key \s})))

  (it "translates S to character input"
    (should= {:char \S} (input/event->input {:key :s :raw-key \S}))))

(describe "letter w and W"
  (it "translates w to character input"
    (should= {:char \w} (input/event->input {:key :w :raw-key \w})))

  (it "translates W to character input"
    (should= {:char \W} (input/event->input {:key :w :raw-key \W}))))

(describe "letter x"
  (it "translates x key to character input"
    (should= {:char \x} (input/event->input {:key :x :raw-key \x})))

  (it "translates uppercase X to character input"
    (should= {:char \X} (input/event->input {:key :x :raw-key \X}))))

(describe "enter key"
  (it "translates Enter (newline char) to :confirm"
    (should= :confirm (input/event->input {:raw-key \newline})))

  (it "translates Enter (return char) to :confirm"
    (should= :confirm (input/event->input {:raw-key \return}))))

(describe "escape key"
  (it "translates Escape to :back"
    (should= :back (input/event->input {:raw-key (char 27)}))))

(describe "space key"
  (it "translates Space to :toggle"
    (should= :toggle (input/event->input {:key :space :raw-key \space}))))

(describe "tab key"
  (it "translates Tab to :tab"
    (should= :tab (input/event->input {:key-code 9 :raw-key \tab})))

  (it "translates Shift+Tab to :shift-tab"
    (should= :shift-tab (input/event->input {:key-code 9 :raw-key \tab :modifiers #{:shift}}))))

(describe "backspace key"
  (it "translates Backspace to :backspace"
    (should= :backspace (input/event->input {:raw-key (char 8)}))))

(describe "delete key"
  (it "translates Delete to :delete"
    (should= :delete (input/event->input {:raw-key (char 127)}))))

(describe "home key"
  (it "translates Home to :home via key-code"
    (should= :home (input/event->input {:key-code 36 :raw-key (char 65535)}))))

(describe "end key"
  (it "translates End to :end via key-code"
    (should= :end (input/event->input {:key-code 35 :raw-key (char 65535)}))))

(describe "page up key"
  (it "translates Page Up to :page-up via key-code"
    (should= :page-up (input/event->input {:key-code 33 :raw-key (char 65535)}))))

(describe "page down key"
  (it "translates Page Down to :page-down via key-code"
    (should= :page-down (input/event->input {:key-code 34 :raw-key (char 65535)}))))

(describe "character with Ctrl modifier"
  (it "translates Ctrl+a to character with ctrl modifier"
    (should= {:char \a :mods #{:ctrl}} (input/event->input {:raw-key \a :modifiers #{:ctrl}})))

  (it "translates Ctrl+b to character with ctrl modifier"
    (should= {:char \b :mods #{:ctrl}} (input/event->input {:raw-key \b :modifiers #{:ctrl}}))))

(describe "unknown and nil"
  (it "returns nil for nil input"
    (should= nil (input/event->input nil)))

  (it "returns nil for unknown keys"
    (should= nil (input/event->input {:key :unknown :raw-key (char 65535)}))))

(describe "printable characters"
  (it "translates a to character input"
    (should= {:char \a} (input/event->input {:raw-key \a})))

  (it "translates Z to character input"
    (should= {:char \Z} (input/event->input {:raw-key \Z})))

  (it "translates 7 to character input"
    (should= {:char \7} (input/event->input {:raw-key \7})))

  (it "translates ? to character input"
    (should= {:char \?} (input/event->input {:raw-key \?}))))

(describe "printable ASCII codes that were previously hijacked"
  (it "translates ! (char 33) to character, not page-up"
    (should= {:char (char 33)} (input/event->input {:raw-key (char 33)})))

  (it "translates \" (char 34) to character, not page-down"
    (should= {:char (char 34)} (input/event->input {:raw-key (char 34)})))

  (it "translates # (char 35) to character, not end"
    (should= {:char (char 35)} (input/event->input {:raw-key (char 35)})))

  (it "translates $ (char 36) to character, not home"
    (should= {:char (char 36)} (input/event->input {:raw-key (char 36)}))))

(describe "escape?"
  (it "returns true for Escape raw key"
    (should= true (input/escape? {:raw-key (char 27)})))

  (it "returns false for other keys"
    (should= false (input/escape? {:raw-key \a}))
    (should= false (input/escape? {:raw-key \newline})))

  (it "returns false for nil event"
    (should= false (input/escape? nil)))

  (it "returns false when raw-key is missing"
    (should= false (input/escape? {:key :up}))))
