(ns veil.ui.qa.session-spec
  (:require [speclj.core :refer :all]
            [veil.ui.qa.session :as session]))

(def ^:private procedure-text
  "{:script \"specs/qa/menu.keys\" :expect [{:key :down} {:event :menu/selection-changed :to :options}]}")

(def ^:private passing-log
  "{:log/version 1}\n{:tick 1 :key :down}\n{:tick 1 :event :menu/selection-changed :to :options}\n")

(defn- fake-io
  "An io map over an in-memory file table. Records every spawn in the spawns atom."
  [files spawns & {:keys [child listing]}]
  {:exists?         #(contains? files %)
   :slurp           files
   :spawn           (fn [script log] (swap! spawns conj [script log]) (or child {:exit 0}))
   :list-procedures (constantly (or listing []))})

(defn- files-for [slug log]
  {(str "specs/qa/" slug ".edn") procedure-text
   (str "target/qa/" slug ".log.edn") log})

(describe "run-one"
  (it "starts the game on the procedure's script, logging under target/qa"
    (let [spawns (atom [])]
      (session/run-one (fake-io (files-for "menu" passing-log) spawns) "menu")
      (should= [["specs/qa/menu.keys" "target/qa/menu.log.edn"]] @spawns)))

  (it "reports each expectation and a PASS line when the log matches"
    (let [result (session/run-one (fake-io (files-for "menu" passing-log) (atom [])) "menu")]
      (should= {:out ["seen     {:key :down}"
                      "seen     {:event :menu/selection-changed, :to :options}"
                      "QA menu: PASS"]
                :err []
                :status 0}
               result)))

  (it "fails with a count of the missing entries"
    (let [log "{:log/version 1}\n{:tick 1 :key :down}\n"
          result (session/run-one (fake-io (files-for "menu" log) (atom [])) "menu")]
      (should= 1 (:status result))
      (should= "QA menu: FAIL (1 missing)" (last (:out result)))))

  (it "rejects an unknown slug without starting the game"
    (let [spawns (atom [])
          result (session/run-one (fake-io {} spawns) "nope")]
      (should= [] @spawns)
      (should= {:out [] :err ["no QA procedure for nope (expected specs/qa/nope.edn)"] :status 1}
               result)))

  (it "rejects a malformed procedure without starting the game"
    (let [spawns (atom [])
          files {"specs/qa/menu.edn" "{:script \"x.keys\"}"}
          result (session/run-one (fake-io files spawns) "menu")]
      (should= [] @spawns)
      (should= {:out [] :err ["missing :expect"] :status 1} result)))

  (it "reports a game that timed out"
    (let [result (session/run-one (fake-io (files-for "menu" passing-log) (atom [])
                                           :child {:timed-out? true})
                                  "menu")]
      (should= {:out [] :err ["game did not finish within 60s"] :status 1} result)))

  (it "reports a game that exited with an error, without reading the log"
    (let [result (session/run-one (fake-io {"specs/qa/menu.edn" procedure-text} (atom [])
                                           :child {:exit 2})
                                  "menu")]
      (should= {:out [] :err ["game exited with code 2"] :status 1} result)))

  (it "reports a log that cannot be read"
    (let [result (session/run-one (fake-io (files-for "menu" "garbage\n") (atom [])) "menu")]
      (should= {:out [] :err ["log has no version line"] :status 1} result))))

(defn- run [io args]
  (let [emitted (atom [])
        status (session/run io #(swap! emitted conj %) args)]
    {:status status :emitted @emitted}))

(describe "run"
  (it "runs one procedure for a slug and emits its result"
    (let [{:keys [status emitted]} (run (fake-io (files-for "menu" passing-log) (atom [])) ["menu"])]
      (should= 0 status)
      (should= 1 (count emitted))
      (should= "QA menu: PASS" (last (:out (first emitted))))))

  (it "exits 1 for a slug that fails"
    (let [{:keys [status]} (run (fake-io {} (atom [])) ["nope"])]
      (should= 1 status)))

  (it "prints usage for no arguments"
    (let [{:keys [status emitted]} (run (fake-io {} (atom [])) [])]
      (should= 1 status)
      (should= [{:out [] :err ["Usage: bb qa <slug> | bb qa --all"] :status 1}] emitted)))

  (it "prints usage for an unknown flag"
    (let [{:keys [status emitted]} (run (fake-io {} (atom [])) ["--bogus"])]
      (should= 1 status)
      (should= ["Usage: bb qa <slug> | bb qa --all"] (:err (first emitted)))))

  (it "runs no procedure for a flag"
    (let [spawns (atom [])]
      (run (fake-io (files-for "menu" passing-log) spawns) ["--bogus"])
      (should= [] @spawns))))

(describe "run --all"
  (it "runs every listed procedure in order, then emits the summary"
    (let [spawns (atom [])
          files (merge (files-for "b" passing-log) (files-for "a" passing-log))
          io (fake-io files spawns :listing ["b.edn" "a.edn" "notes.md"])
          {:keys [status emitted]} (run io ["--all"])]
      (should= 0 status)
      (should= [["specs/qa/menu.keys" "target/qa/a.log.edn"]
                ["specs/qa/menu.keys" "target/qa/b.log.edn"]]
               @spawns)
      (should= ["QA a: PASS" "QA b: PASS" "QA all: 2 passed, 0 failed"]
               (mapv (comp last :out) emitted))))

  (it "emits each procedure as it finishes, before running the next"
    (let [order (atom [])
          files (merge (files-for "a" passing-log) (files-for "b" passing-log))
          io (assoc (fake-io files (atom []) :listing ["a.edn" "b.edn"])
                    :spawn (fn [_ log] (swap! order conj [:spawn log]) {:exit 0}))]
      (session/run io #(swap! order conj [:emit (last (:out %))]) ["--all"])
      (should= [[:spawn "target/qa/a.log.edn"] [:emit "QA a: PASS"]
                [:spawn "target/qa/b.log.edn"] [:emit "QA b: PASS"]
                [:emit "QA all: 2 passed, 0 failed"]]
               @order)))

  (it "exits 1 and names the failed procedures in the summary"
    (let [files (merge (files-for "a" passing-log)
                       (files-for "b" "{:log/version 1}\n"))
          io (fake-io files (atom []) :listing ["a.edn" "b.edn"])
          {:keys [status emitted]} (run io ["--all"])]
      (should= 1 status)
      (should= "QA all: 1 passed, 1 failed (b)" (last (:out (last emitted))))))

  (it "fails when there are no procedures"
    (let [{:keys [status emitted]} (run (fake-io {} (atom []) :listing ["notes.md"]) ["--all"])]
      (should= 1 status)
      (should= [{:out [] :err ["no QA procedures in specs/qa"] :status 1}] emitted))))
