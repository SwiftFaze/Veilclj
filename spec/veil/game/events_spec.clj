(ns veil.game.events-spec
  (:require [speclj.core :refer :all]
            [veil.game.events :as events]
            [veil.game.state :as state]))

(describe "between"
  (it "reports selection change from New Game to Options"
    (let [before (state/initial)
          after (state/handle-input before :down)]
      (should= [{:event :menu/selection-changed :to :options}]
               (events/between before after))))

  (it "reports selection change from New Game to Quit"
    (let [before (state/initial)
          after (-> before (state/handle-input :down) (state/handle-input :down))]
      (should= [{:event :menu/selection-changed :to :quit}]
               (events/between before after))))

  (it "reports screen change from main-menu to map"
    (let [before (state/initial)
          after (state/handle-input before :confirm)]
      (should= [{:event :screen/changed :from :main-menu :to :map}]
               (events/between before after))))

  (it "reports selection and screen change when navigating"
    (let [before (state/initial)
          after (-> before (state/handle-input :down) (state/handle-input :confirm))]
      (should= [{:event :menu/selection-changed :to :options}
                {:event :screen/changed :from :main-menu :to :options}]
               (events/between before after))))

  (it "reports selection and over when quit is selected"
    (let [before (state/initial)
          after (-> before (state/handle-input :down) (state/handle-input :down) (state/handle-input :confirm))]
      (should= [{:event :menu/selection-changed :to :quit}
                {:event :game/over}]
               (events/between before after))))

  (it "reports no events when nothing changes"
    (let [before (state/initial)
          after before]
      (should= [] (events/between before after))))

  (it "reports no events when selection is already at Quit"
    (let [s1 (-> (state/initial) (state/handle-input :down) (state/handle-input :down))
          s2 s1]
      (should= [] (events/between s1 s2)))))
