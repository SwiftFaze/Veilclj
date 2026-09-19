(ns veil.game.theme-spec
  (:require [speclj.core :refer :all]
            [veil.game.theme :as theme]
            [veil.game.state :as state]))

(describe "default-id"
  (it "is 'core:default'"
    (should= "core:default" theme/default-id)))

(describe "color"
  (it "returns the color for a key in the active theme"
    (let [s {:themes {"core:default" {:NORMAL_TEXT [255 255 255]}}
             :active-theme "core:default"}]
      (should= [255 255 255] (theme/color s :NORMAL_TEXT))))

  (it "throws when the key is not in the active theme"
    (let [s {:themes {"core:default" {:NORMAL_TEXT [255 255 255]}}
             :active-theme "core:default"}]
      (should-throw Exception #"UNKNOWN_KEY" (theme/color s :UNKNOWN_KEY))))

  (it "throws when the active theme is not in the state"
    (let [s {:themes {"core:default" {:NORMAL_TEXT [255 255 255]}}
             :active-theme "missing:theme"}]
      (should-throw Exception #"missing:theme" (theme/color s :NORMAL_TEXT)))))

(describe "active-id"
  (it "returns the active theme id"
    (let [s {:active-theme "core:default"}]
      (should= "core:default" (theme/active-id s)))))

(describe "activate"
  (it "sets the active theme to the given id"
    (let [s {:themes {"core:default" {:NORMAL_TEXT [255 255 255]}
                      "other:theme" {:NORMAL_TEXT [100 100 100]}}
             :active-theme "core:default"}]
      (should= "other:theme" (:active-theme (theme/activate s "other:theme")))))

  (it "throws when the id is not in the themes"
    (let [s {:themes {"core:default" {:NORMAL_TEXT [255 255 255]}}
             :active-theme "core:default"}]
      (should-throw Exception #"missing:theme" (theme/activate s "missing:theme")))))
