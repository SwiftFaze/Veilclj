(ns veil.ui.qa.play-spec
  (:require [speclj.core :refer :all]
            [veil.ui.qa.play :as play]
            [veil.ui.qa.script :as script]
            [veil.ui.input :as input]
            [veil.game.state :as state]))

(defn- simple-handler [s ev]
  (state/handle-input s (input/event->input ev)))

(describe "press"
  (it "presses a key and logs it"
    (let [result (play/press simple-handler (state/initial) 1 "Down")]
      (should= :down (get-in result [:entries 0 :key]))
      (should= 1 (get-in result [:entries 0 :tick]))))

  (it "includes events from the state change"
    (let [result (play/press simple-handler (state/initial) 1 "Down")]
      (should= :options (get-in result [:entries 1 :to]))
      (should= :menu/selection-changed (get-in result [:entries 1 :event]))))

  (it "includes tick in events"
    (let [result (play/press simple-handler (state/initial) 5 "Down")]
      (should= 5 (get-in result [:entries 1 :tick]))))

  (it "returns updated state"
    (let [result (play/press simple-handler (state/initial) 1 "Down")]
      (should= "Options" (state/selected-item (:state result))))))

(describe "play"
  (it "plays a single key"
    (let [steps [{:tick 1 :key "Down"}]
          result (play/play simple-handler (state/initial) steps)]
      (should= true (:finished? result))
      (should= 2 (count (:entries result)))
      (should= "Options" (state/selected-item (:state result)))))

  (it "plays multiple keys in sequence"
    (let [steps [{:tick 1 :key "Down"} {:tick 2 :key "Enter"}]
          result (play/play simple-handler (state/initial) steps)]
      (should= true (:finished? result))
      (should= :options (state/screen (:state result)))))

  (it "collects all entries in order"
    (let [steps [{:tick 1 :key "Down"} {:tick 2 :key "Enter"}]
          result (play/play simple-handler (state/initial) steps)]
      (should= 1 (get-in result [:entries 0 :tick]))
      (should= :down (get-in result [:entries 0 :key]))
      (should= 2 (get-in result [:entries 2 :tick]))
      (should= :enter (get-in result [:entries 2 :key])))))

(describe "handle-event"
  (it "logs the key the event carries, then the events it caused"
    (let [event (script/key->event "Enter")
          result (play/handle-event simple-handler (state/initial) 4 event)]
      (should= [{:tick 4 :key :enter}
                {:tick 4 :event :screen/changed :from :main-menu :to :map}]
               (:entries result))
      (should= :map (state/screen (:state result)))))

  (it "logs an event with no key as :unknown"
    (let [result (play/handle-event simple-handler (state/initial) 2 {:raw-key \x})]
      (should= [{:tick 2 :key :unknown}] (:entries result))))

  (it "gives a live event the same entries as the scripted press of that key"
    (let [pressed (play/press simple-handler (state/initial) 3 "Down")
          live (play/handle-event simple-handler (state/initial) 3 (script/key->event "Down"))]
      (should= (:entries pressed) (:entries live)))))
