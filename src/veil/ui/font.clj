(ns veil.ui.font
  "Load and open a font file."
  (:require [clojure.java.io :as io])
  (:import [java.awt Font]))

(defn open
  "Open a font from the file system.
   mods-dir is the root directory where mods are stored (relative or absolute).
   font is {:file \"<mod>/fonts/<name>.ttf\" :size 20}.
   Returns {:font-path absolute-path :size 20} on success, or {:error msg} on failure.
   The error message names the file and says it is not a usable font.
   A missing file and a file that is not a font fail the same way: createFont
   throws for both, and the player needs only to know which file to fix."
  [mods-dir font]
  (let [file-path (str mods-dir "/" (:file font))
        file (io/file file-path)]
    (try
      (Font/createFont Font/TRUETYPE_FONT file)
      {:font-path (.getAbsolutePath file) :size (:size font)}
      (catch Exception _
        {:error (str file-path " is not a usable font")}))))
