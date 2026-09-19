(ns veil.mods.themes-spec
  (:require [speclj.core :refer :all]
            [clojure.spec.alpha :as s]
            [veil.mods.themes :as themes]
            [veil.mods.loader :as loader]
            [veil.mods.fixtures :as f]))

(describe "channel?"
  (it "accepts integers from 0 to 255"
    (should (themes/channel? 0))
    (should (themes/channel? 127))
    (should (themes/channel? 255)))

  (it "rejects negative integers"
    (should-not (themes/channel? -1)))

  (it "rejects integers above 255"
    (should-not (themes/channel? 256)))

  (it "rejects non-integers"
    (should-not (themes/channel? 12.5))
    (should-not (themes/channel? "ff"))))

(describe "construct"
  (it "returns all 19 color keys with resolved colors"
    (let [colors {:SELECTED_HIGHLIGHT {:r 1 :g 2 :b 3}
                  :SELECTED_TEXT {:r 4 :g 5 :b 6}
                  :NORMAL_TEXT {:r 7 :g 8 :b 9}
                  :DIMMED_TEXT {:r 10 :g 11 :b 12}
                  :BACKGROUND {:r 13 :g 14 :b 15}
                  :INVALID_HIGHLIGHT {:r 16 :g 17 :b 18}
                  :VALID_HIGHLIGHT {:r 19 :g 20 :b 21}
                  :TABLE_HEADER_BACKGROUND {:r 22 :g 23 :b 24}
                  :BORDER {:r 25 :g 26 :b 27}
                  :SCROLLBAR_THUMB {:r 28 :g 29 :b 30}
                  :ACCENT {:r 31 :g 32 :b 33}
                  :WINDOW_BORDER {:r 34 :g 35 :b 36}
                  :TABLE_HEADER_TEXT {:r 37 :g 38 :b 39}}
          result (themes/construct {:id "core:default" :colors colors})]
      (should= [1 2 3] (:SELECTED_HIGHLIGHT result))
      (should= [4 5 6] (:SELECTED_TEXT result))
      (should= [7 8 9] (:NORMAL_TEXT result))
      (should= [10 11 12] (:DIMMED_TEXT result))
      (should= [13 14 15] (:BACKGROUND result))
      (should= [16 17 18] (:INVALID_HIGHLIGHT result))
      (should= [19 20 21] (:VALID_HIGHLIGHT result))
      (should= [22 23 24] (:TABLE_HEADER_BACKGROUND result))
      (should= [25 26 27] (:BORDER result))
      (should= [28 29 30] (:SCROLLBAR_THUMB result))
      (should= [31 32 33] (:ACCENT result))
      (should= [34 35 36] (:WINDOW_BORDER result))
      (should= [37 38 39] (:TABLE_HEADER_TEXT result))))

  (it "fills SUCCESS from VALID_HIGHLIGHT when not provided"
    (let [colors {:SELECTED_HIGHLIGHT {:r 1 :g 2 :b 3}
                  :SELECTED_TEXT {:r 4 :g 5 :b 6}
                  :NORMAL_TEXT {:r 7 :g 8 :b 9}
                  :DIMMED_TEXT {:r 10 :g 11 :b 12}
                  :BACKGROUND {:r 13 :g 14 :b 15}
                  :INVALID_HIGHLIGHT {:r 16 :g 17 :b 18}
                  :VALID_HIGHLIGHT {:r 19 :g 20 :b 21}
                  :TABLE_HEADER_BACKGROUND {:r 22 :g 23 :b 24}
                  :BORDER {:r 25 :g 26 :b 27}
                  :SCROLLBAR_THUMB {:r 28 :g 29 :b 30}
                  :ACCENT {:r 31 :g 32 :b 33}
                  :WINDOW_BORDER {:r 34 :g 35 :b 36}
                  :TABLE_HEADER_TEXT {:r 37 :g 38 :b 39}}
          result (themes/construct {:id "core:default" :colors colors})]
      (should= [19 20 21] (:SUCCESS result))))

  (it "uses SUCCESS when provided"
    (let [colors {:SELECTED_HIGHLIGHT {:r 1 :g 2 :b 3}
                  :SELECTED_TEXT {:r 4 :g 5 :b 6}
                  :NORMAL_TEXT {:r 7 :g 8 :b 9}
                  :DIMMED_TEXT {:r 10 :g 11 :b 12}
                  :BACKGROUND {:r 13 :g 14 :b 15}
                  :INVALID_HIGHLIGHT {:r 16 :g 17 :b 18}
                  :VALID_HIGHLIGHT {:r 19 :g 20 :b 21}
                  :TABLE_HEADER_BACKGROUND {:r 22 :g 23 :b 24}
                  :BORDER {:r 25 :g 26 :b 27}
                  :SCROLLBAR_THUMB {:r 28 :g 29 :b 30}
                  :ACCENT {:r 31 :g 32 :b 33}
                  :WINDOW_BORDER {:r 34 :g 35 :b 36}
                  :TABLE_HEADER_TEXT {:r 37 :g 38 :b 39}
                  :SUCCESS {:r 100 :g 101 :b 102}}
          result (themes/construct {:id "core:default" :colors colors})]
      (should= [100 101 102] (:SUCCESS result))))

  (it "fills all 6 optional keys from their fallbacks"
    (let [colors {:SELECTED_HIGHLIGHT {:r 1 :g 2 :b 3}
                  :SELECTED_TEXT {:r 4 :g 5 :b 6}
                  :NORMAL_TEXT {:r 7 :g 8 :b 9}
                  :DIMMED_TEXT {:r 10 :g 11 :b 12}
                  :BACKGROUND {:r 13 :g 14 :b 15}
                  :INVALID_HIGHLIGHT {:r 16 :g 17 :b 18}
                  :VALID_HIGHLIGHT {:r 19 :g 20 :b 21}
                  :TABLE_HEADER_BACKGROUND {:r 22 :g 23 :b 24}
                  :BORDER {:r 25 :g 26 :b 27}
                  :SCROLLBAR_THUMB {:r 28 :g 29 :b 30}
                  :ACCENT {:r 31 :g 32 :b 33}
                  :WINDOW_BORDER {:r 34 :g 35 :b 36}
                  :TABLE_HEADER_TEXT {:r 37 :g 38 :b 39}}
          result (themes/construct {:id "core:default" :colors colors})]
      (should= [19 20 21] (:SUCCESS result))
      (should= [16 17 18] (:ERROR result))
      (should= [31 32 33] (:WARNING result))
      (should= [31 32 33] (:INFO result))
      (should= [31 32 33] (:FOCUSED_BORDER result))
      (should= [13 14 15] (:SHADOW result)))))

(describe "all"
  (it "returns a map of theme-id to resolved colors for every registered theme"
    (let [mods-data (f/mods
                      (f/manifest "core")
                      ["core/themes/theme1.json" "{\"id\": \"core:theme1\", \"colors\": {\"SELECTED_HIGHLIGHT\": {\"r\": 1, \"g\": 2, \"b\": 3}, \"SELECTED_TEXT\": {\"r\": 4, \"g\": 5, \"b\": 6}, \"NORMAL_TEXT\": {\"r\": 7, \"g\": 8, \"b\": 9}, \"DIMMED_TEXT\": {\"r\": 10, \"g\": 11, \"b\": 12}, \"BACKGROUND\": {\"r\": 13, \"g\": 14, \"b\": 15}, \"INVALID_HIGHLIGHT\": {\"r\": 16, \"g\": 17, \"b\": 18}, \"VALID_HIGHLIGHT\": {\"r\": 19, \"g\": 20, \"b\": 21}, \"BORDER\": {\"r\": 25, \"g\": 26, \"b\": 27}, \"SCROLLBAR_THUMB\": {\"r\": 28, \"g\": 29, \"b\": 30}, \"ACCENT\": {\"r\": 31, \"g\": 32, \"b\": 33}, \"WINDOW_BORDER\": {\"r\": 34, \"g\": 35, \"b\": 36}, \"TABLE_HEADER_TEXT\": {\"r\": 37, \"g\": 38, \"b\": 39}, \"TABLE_HEADER_BACKGROUND\": {\"r\": 22, \"g\": 23, \"b\": 24}}}"]
                      )
          load-result (loader/load-mods mods-data [themes/content-type])
          registry (:registry load-result)
          all-themes (themes/all registry)]
      (should (contains? all-themes "core:theme1"))
      (should= [1 2 3] (:SELECTED_HIGHLIGHT (get all-themes "core:theme1"))))))

(describe "startup"
  (it "returns themes and no error when default-id is registered"
    (let [mods-data (f/mods
                      (f/manifest "core")
                      ["core/themes/default.json" "{\"id\": \"core:default\", \"colors\": {\"SELECTED_HIGHLIGHT\": {\"r\": 1, \"g\": 2, \"b\": 3}, \"SELECTED_TEXT\": {\"r\": 4, \"g\": 5, \"b\": 6}, \"NORMAL_TEXT\": {\"r\": 7, \"g\": 8, \"b\": 9}, \"DIMMED_TEXT\": {\"r\": 10, \"g\": 11, \"b\": 12}, \"BACKGROUND\": {\"r\": 13, \"g\": 14, \"b\": 15}, \"INVALID_HIGHLIGHT\": {\"r\": 16, \"g\": 17, \"b\": 18}, \"VALID_HIGHLIGHT\": {\"r\": 19, \"g\": 20, \"b\": 21}, \"BORDER\": {\"r\": 25, \"g\": 26, \"b\": 27}, \"SCROLLBAR_THUMB\": {\"r\": 28, \"g\": 29, \"b\": 30}, \"ACCENT\": {\"r\": 31, \"g\": 32, \"b\": 33}, \"WINDOW_BORDER\": {\"r\": 34, \"g\": 35, \"b\": 36}, \"TABLE_HEADER_TEXT\": {\"r\": 37, \"g\": 38, \"b\": 39}, \"TABLE_HEADER_BACKGROUND\": {\"r\": 22, \"g\": 23, \"b\": 24}}}"]
                      )
          load-result (loader/load-mods mods-data [themes/content-type])
          registry (:registry load-result)
          result (themes/startup registry "core:default")]
      (should (contains? result :themes))
      (should-not (contains? result :error))))

  (it "returns error when default-id is not registered"
    (let [mods-data (f/mods (f/manifest "core"))
          load-result (loader/load-mods mods-data [themes/content-type])
          registry (:registry load-result)
          result (themes/startup registry "core:default")]
      (should (contains? result :error))
      (should= 1 (:exit-status result))
      (should (.contains (:error result) "core:default")))))
