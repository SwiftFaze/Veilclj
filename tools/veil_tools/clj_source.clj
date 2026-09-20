(ns veil-tools.clj-source
  "Reading Clojure source the way the repo's own tools need it: top-level forms
  carrying their line numbers, with *read-eval* off. Shared so that a tool
  analysing source never grows its own copy of the reader setup."
  (:import [clojure.lang LineNumberingPushbackReader]
           [java.io StringReader]))

(defn read-forms
  "Every top-level form in source-text; list forms carry :line metadata."
  [source-text]
  (binding [*read-eval* false]
    (let [reader (LineNumberingPushbackReader. (StringReader. source-text))]
      (->> (repeatedly #(read reader false ::eof))
           (take-while #(not= ::eof %))
           vec))))
