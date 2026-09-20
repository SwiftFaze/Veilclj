(ns veil-tools.jev
  "SPIKE: the one place a TypeSafe System One request leaves the machine.
  Everything that decides anything is pure and lives in the calling namespace;
  this only posts a payload and hands back the parsed answer, the way
  veil.ui.qa.runner holds the process spawn and nothing else."
  (:require [babashka.http-client :as http]
            [cheshire.core :as json]))

(def ^:private endpoint "https://api.typesafe.ai/v1/systemone")

(defn ask
  "POST one request payload. Returns the parsed response, or {:error msg} — a
  failed call is a reported error, never a silent pass: this check is advisory,
  so it must not be possible to switch it off by breaking the network."
  [payload]
  (if-let [api-key (System/getenv "TYPESAFE_API_KEY")]
    (try
      (let [response (http/post endpoint
                                {:headers {"Authorization" (str "Bearer " api-key)
                                           "Content-Type" "application/json"}
                                 :body (json/generate-string payload)
                                 :throw false})]
        (if (= 200 (:status response))
          (json/parse-string (:body response) true)
          {:error (str "HTTP " (:status response) " " (:body response))}))
      (catch Exception e
        {:error (.getMessage e)}))
    {:error "TYPESAFE_API_KEY is not set"}))
