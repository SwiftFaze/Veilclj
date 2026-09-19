(ns veil.ui.qa.script
  "Parse key scripts: sequences of key names, waits, and comments.")

(def ^:private special-keys #{"Up" "Down" "Left" "Right" "Enter" "Esc" "Space"})

(defn- is-valid-key? [text]
  (or (contains? special-keys text)
      (and (= 1 (count text)) (Character/isLetterOrDigit (first text)))))

(defn- parse-wait [text]
  (if-let [match (re-matches #"(\d+)" text)]
    (let [n (Long/parseLong (second match))]
      {:wait n})
    nil))

(defn- parse-line [line-num line]
  (let [trimmed (clojure.string/trim line)
        without-comment (clojure.string/replace trimmed #"#.*" "")
        clean (clojure.string/trim without-comment)]
    (cond
      (empty? clean) {:blank? true}
      (clojure.string/starts-with? clean "wait")
      (let [parts (clojure.string/split clean #"\s+" 2)
            wait-arg (or (second parts) "")]
        (if-let [wait-val (parse-wait wait-arg)]
          wait-val
          {:error (str "line " line-num ": wait needs a whole number")}))
      (is-valid-key? clean) {:key clean}
      :else {:error (str "line " line-num ": unknown key \"" clean "\"")})))

(defn parse
  "Parse a script text into steps or an error.
   Returns {:steps [...]} or {:error \"...\"}."
  [text]
  (let [lines (clojure.string/split text #"\r?\n")
        parsed (map-indexed (fn [idx line] (parse-line (inc idx) line)) lines)
        error (first (filter :error parsed))]
    (if error
      error
      (let [steps (loop [parsed parsed
                        steps []
                        tick 1]
             (if (empty? parsed)
               steps
               (let [item (first parsed)
                     rest (rest parsed)]
                 (cond
                   (:blank? item) (recur rest steps tick)
                   (:key item) (recur rest (conj steps {:tick tick :key (:key item)}) (inc tick))
                   (:wait item) (recur rest steps (+ tick (:wait item)))
                   :else (recur rest steps tick)))))]
        {:steps steps}))))

(defn key->event
  "Build a Quil-shaped event map for a key name."
  [key-name]
  (case key-name
    "Down" {:key :down :key-code 40 :raw-key (char 65535)}
    "Up" {:key :up :key-code 38 :raw-key (char 65535)}
    "Left" {:key :left :key-code 37 :raw-key (char 65535)}
    "Right" {:key :right :key-code 39 :raw-key (char 65535)}
    "Enter" {:raw-key \newline}
    "Esc" {:raw-key (char 27)}
    "Space" {:key :space :raw-key \space}
    (let [c (first key-name)]
      {:key (keyword (clojure.string/lower-case key-name))
       :raw-key c})))

(defn key-keyword
  "Get the keyword used in log entries for a key name."
  [key-name]
  (case key-name
    "Down" :down
    "Up" :up
    "Left" :left
    "Right" :right
    "Enter" :enter
    "Esc" :esc
    "Space" :space
    (keyword (clojure.string/lower-case key-name))))

(defn event->key-keyword
  "Convert a Quil-shaped event to the keyword used in log entries.
   Handles raw-key (newline/return -> :enter, ESC -> :esc) or :key field."
  [event]
  (let [raw-key (:raw-key event)]
    (cond
      (or (= raw-key \newline) (= raw-key \return)) :enter
      (= raw-key (char 27)) :esc
      (keyword? (:key event)) (:key event)
      :else :unknown)))
