(ns veil.ui.qa.mode-spec
  (:require [speclj.core :refer :all]
            [veil.game.state :as state]
            [veil.ui.input :as input]
            [veil.ui.qa.driver :as driver]
            [veil.ui.qa.mode :as mode]
            [veil.ui.qa.script :as script]))

(defn- handle [s event]
  (state/handle-input s (input/event->input event)))

(defn- quit-state []
  (-> (state/initial) (state/handle-input :down) (state/handle-input :down) (state/handle-input :confirm)))

(def ^:private steps [{:tick 1 :key "Down"} {:tick 2 :key "Enter"}])

(describe "plan"
  (it "plans plain play when no flags are given"
    (should= {:qa {:driver nil :log-path nil}}
             (mode/plan [] (fn [_] (throw (Exception. "must not read"))))))

  (it "plans a log without a driver for --log alone"
    (should= {:qa {:driver nil :log-path "target/qa/x.log.edn"}}
             (mode/plan ["--log" "target/qa/x.log.edn"] (fn [_] (throw (Exception. "must not read"))))))

  (it "plans a driver on the script's steps for --keys"
    (let [reads (atom [])
          read-script (fn [path] (swap! reads conj path) {:steps steps})
          result (mode/plan ["--keys" "a.keys" "--log" "b.edn"] read-script)]
      (should= ["a.keys"] @reads)
      (should= {:qa {:driver (driver/new-driver steps) :log-path "b.edn"}} result)))

  (it "plans a finished-at-once driver for an empty script"
    (should= (driver/new-driver [])
             (get-in (mode/plan ["--keys" "a.keys"] (fn [_] {:steps []})) [:qa :driver])))

  (it "reports a launch error without reading the script"
    (should= {:error "unknown argument --bogus"}
             (mode/plan ["--bogus"] (fn [_] (throw (Exception. "must not read"))))))

  (it "reports a script error"
    (should= {:error "a.keys: line 1: unknown key \"Bogus\""}
             (mode/plan ["--keys" "a.keys"]
                        (fn [_] {:error "a.keys: line 1: unknown key \"Bogus\""})))))

(describe "frame"
  (it "leaves the game alone and keeps running with no driver"
    (let [s (state/initial)
          result (mode/frame {:driver nil :log-path nil} handle s)]
      (should= s (:state result))
      (should= [] (:entries result))
      (should= false (:exit? result))))

  (it "exits when the game is over and no driver is running"
    (let [result (mode/frame {:driver nil :log-path nil} handle (quit-state))]
      (should= true (:exit? result))))

  (it "exits on the frame that presses the last step"
    (let [qa {:driver (driver/new-driver [{:tick 1 :key "Down"}]) :log-path nil}
          result (mode/frame qa handle (state/initial))]
      (should= true (:exit? result))))

  (it "exits when the script has already run out"
    (let [qa {:driver (driver/new-driver []) :log-path nil}
          result (mode/frame qa handle (state/initial))]
      (should= true (:exit? result))))

  (it "exits when the game ends before the script does"
    (let [qa {:driver (driver/new-driver [{:tick 1 :key "Enter"} {:tick 2 :key "Down"}]) :log-path nil}
          result (mode/frame qa handle (-> (state/initial) (state/handle-input :down) (state/handle-input :down)))]
      (should= true (state/over? (:state result)))
      (should= true (:exit? result))))

  (it "waits without pressing on a frame with no step due"
    (let [qa {:driver (driver/new-driver [{:tick 3 :key "Down"}]) :log-path nil}
          result (mode/frame qa handle (state/initial))]
      (should= [] (:entries result))
      (should= false (:exit? result))
      (should= 1 (get-in result [:qa :driver :tick])))))

(describe "frame with a step due"
  (with result (mode/frame {:driver (driver/new-driver steps) :log-path "x"} handle (state/initial)))

  (it "applies the key to the game state"
    (should= "Options" (state/selected-item (:state @result))))

  (it "returns the key and its events for the log"
    (should= [{:tick 1 :key :down}
              {:tick 1 :event :menu/selection-changed :to :options}]
             (:entries @result)))

  (it "keeps running while steps remain"
    (should= false (:exit? @result)))

  (it "moves the driver past the pressed step"
    (should= [{:tick 2 :key "Enter"}] (get-in @result [:qa :driver :pending])))

  (it "keeps the log path"
    (should= "x" (get-in @result [:qa :log-path]))))

(describe "on-key"
  (let [event (script/key->event "Down")]
    (it "returns the new state and the entries to log when a log was asked for"
      (let [result (mode/on-key {:driver nil :log-path "x"} handle (state/initial) 7 event)]
        (should= "Options" (state/selected-item (:state result)))
        (should= [{:tick 7 :key :down}
                  {:tick 7 :event :menu/selection-changed :to :options}]
                 (:entries result))))

    (it "logs nothing when no log was asked for, but still moves the game"
      (let [result (mode/on-key {:driver nil :log-path nil} handle (state/initial) 7 event)]
        (should= "Options" (state/selected-item (:state result)))
        (should= [] (:entries result))))

    (it "logs nothing while a script is driving, since the driver logs its own presses"
      (let [qa {:driver (driver/new-driver steps) :log-path "x"}
            result (mode/on-key qa handle (state/initial) 7 event)]
        (should= [] (:entries result))))))
