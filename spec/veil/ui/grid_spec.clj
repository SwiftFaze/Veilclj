(ns veil.ui.grid-spec
  (:require [speclj.core :refer :all]
            [veil.ui.grid :as grid]))

(describe "size"
  (it "calculates whole cells that fit the window"
    (should= {:cols 80 :rows 24}
             (grid/size 960 600 12 25)))

  (it "calculates more cells for a wider window"
    (should= {:cols 100 :rows 24}
             (grid/size 1200 600 12 25)))

  (it "calculates more cells for a taller window"
    (should= {:cols 80 :rows 30}
             (grid/size 960 750 12 25)))

  (it "handles large windows"
    (should= {:cols 160 :rows 43}
             (grid/size 1920 1080 12 25)))

  (it "enforces minimum 80 columns in a narrow, tall window"
    (should= {:cols 80 :rows 36}
             (grid/size 600 900 12 25)))

  (it "enforces minimum 24 rows in a wide, short window"
    (should= {:cols 100 :rows 24}
             (grid/size 1200 300 12 25)))

  (it "handles partial cell dimensions"
    (should= {:cols 80 :rows 24}
             (grid/size 971 619 12 25)))

  (it "includes partial cell (next whole cell)"
    (should= {:cols 81 :rows 25}
             (grid/size 972 625 12 25))))

(describe "cell-size"
  (it "converts text metrics to integers"
    (let [result (grid/cell-size 12.0 15.5 9.5)]
      (should= 12 (:w result))
      (should= 25 (:h result))))

  (it "ceils the width"
    (should= 13 (:w (grid/cell-size 12.5 15.5 9.5))))

  (it "ceils the height sum"
    (should= 26 (:h (grid/cell-size 12.0 15.5 9.9)))))
