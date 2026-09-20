(ns veil-tools.jev-cli
  "SPIKE: `bb jev <file>` - post System One payloads and print the answers.

  Deliberately dumb transport. The judgement design (what to ask, which
  primitive, what the criteria say, how to compose the answers) lives in the
  skill that writes the payload, not here, so a new kind of check needs a new
  question rather than a new Clojure namespace.

  One JSON object in the file -> one pretty response. One object per line
  (JSONL) -> one response per line, in the same order, each carrying back the
  payload's own \"id\" so a caller can match them up."
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [veil-tools.jev :as jev]))

(defn- answer-for
  "Post one payload, keeping its id with the answer. The id is ours, not the
  API's, so it is stripped before the request goes out."
  [payload]
  (let [id (get payload "id")
        response (jev/ask (dissoc payload "id"))]
    (cond-> response id (assoc :id id))))

(defn- payloads
  "Every JSON value in the file, whether it holds one pretty-printed object or
  one per line. Read as a sequence rather than a single parse: parsing the
  whole text as one value silently returns the first object and drops the rest."
  [text]
  (with-open [reader (java.io.BufferedReader. (java.io.StringReader. text))]
    (vec (json/parsed-seq reader false))))

(defn run
  "Exit 0 when every payload got an answer, 1 when any failed - a caller
  scripting this needs to tell a verdict from a dead network."
  [& args]
  (let [path (first (remove #(str/starts-with? % "--") args))
        requests (payloads (slurp path))
        responses (doall (pmap answer-for requests))]
    (if (= 1 (count responses))
      (println (json/generate-string (first responses) {:pretty true}))
      (run! #(println (json/generate-string %)) responses))
    (flush)
    (if (some :error responses) 1 0)))
