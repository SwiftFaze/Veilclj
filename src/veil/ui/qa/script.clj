(ns veil.ui.qa.script
  "Parse key scripts: sequences of key names, waits, and comments."
  (:require [clojure.string :as str]))

(def ^:private special-keys #{"Down" "Up" "Left" "Right" "Enter" "Esc" "Space"})

(defn- valid-key? [text]
  (or (contains? special-keys text)
      (and (= 1 (count text)) (Character/isLetterOrDigit (first text)))))

(defn- parse-wait [text]
  (when-let [[_ n] (re-matches #"(\d+)" text)]
    {:wait (Long/parseLong n)}))

(defn- strip-comment [line]
  (str/trim (str/replace (str/trim line) #"#.*" "")))

(defn- parse-wait-line [line-num clean]
  (let [arg (or (second (str/split clean #"\s+" 2)) "")]
    (or (parse-wait arg)
        {:error (str "line " line-num ": wait needs a whole number")})))

(defn- parse-line [line-num line]
  (let [clean (strip-comment line)]
    (cond
      (empty? clean) {}
      (str/starts-with? clean "wait") (parse-wait-line line-num clean)
      (valid-key? clean) {:key clean}
      :else {:error (str "line " line-num ": unknown key \"" clean "\"")})))

(defn- place
  "Fold one parsed line into {:steps [...] :tick n}: a key takes the current
   tick and advances it by one, a wait skips ticks, a blank line does nothing."
  [{:keys [steps tick] :as acc} item]
  (cond
    (:key item) {:steps (conj steps {:tick tick :key (:key item)}) :tick (inc tick)}
    (:wait item) (assoc acc :tick (+ tick (:wait item)))
    :else acc))

(defn parse
  "Parse a script text into steps or an error.
   Returns {:steps [...]} or {:error \"...\"}."
  [text]
  (let [parsed (map-indexed (fn [idx line] (parse-line (inc idx) line))
                            (str/split text #"\r?\n"))]
    (or (first (filter :error parsed))
        {:steps (:steps (reduce place {:steps [] :tick 1} parsed))})))

(def ^:private named-events
  {"Down"  {:key :down  :key-code 40 :raw-key (char 65535)}
   "Up"    {:key :up    :key-code 38 :raw-key (char 65535)}
   "Left"  {:key :left  :key-code 37 :raw-key (char 65535)}
   "Right" {:key :right :key-code 39 :raw-key (char 65535)}
   "Enter" {:raw-key \newline}
   "Esc"   {:raw-key (char 27)}
   "Space" {:key :space :raw-key \space}})

(defn key-keyword
  "Get the keyword used in log entries for a key name: its lower-cased name."
  [key-name]
  (keyword (str/lower-case key-name)))

(defn key->event
  "Build a Quil-shaped event map for a key name."
  [key-name]
  (or (named-events key-name)
      {:key (key-keyword key-name) :raw-key (first key-name)}))

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
