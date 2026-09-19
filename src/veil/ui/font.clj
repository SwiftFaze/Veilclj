(ns veil.ui.font
  "Load and open a font file."
  (:require [clojure.java.io :as io])
  (:import [java.awt Font]))

(defn open
  "Open a font from the file system.
   mods-dir is the root directory where mods are stored (relative or absolute).
   font is {:file \"<mod>/fonts/<name>.ttf\" :size 20}.
   Returns {:font-path absolute-path :size 20} on success, or {:error msg} on failure.
   The error message names the file and says it is not a usable font."
  [mods-dir font]
  (let [file-path (str mods-dir "/" (:file font))
        f (io/file file-path)]
    (try
      (if (.exists f)
        (let [absolute-path (.getAbsolutePath f)
              file-obj (io/file absolute-path)]
          (Font/createFont Font/TRUETYPE_FONT file-obj)
          {:font-path absolute-path :size (:size font)})
        {:error (str file-path " is not a usable font")})
      (catch Exception e
        {:error (str file-path " is not a usable font")}))))
