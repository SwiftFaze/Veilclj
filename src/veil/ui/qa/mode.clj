(ns veil.ui.qa.mode
  "The sketch's QA mode as pure decisions: the launch plan, what each frame
  does, and what a live key press logs. veil.main only wires these to Quil and
  the log file."
  (:require [veil.game.state :as state]
            [veil.ui.qa.driver :as driver]
            [veil.ui.qa.launch :as launch]
            [veil.ui.qa.play :as play]))

(defn plan
  "Turn launch arguments into {:qa {:driver d-or-nil :log-path p-or-nil}} or
   {:error msg}. read-script (path -> {:steps [...]} or {:error msg}) is passed
   in so this stays free of I/O."
  [args read-script]
  (let [launched (launch/parse-args args)
        script (when (:keys launched) (read-script (:keys launched)))]
    (cond
      (:error launched) launched
      (:error script) script
      :else {:qa {:driver (when script (driver/new-driver (:steps script)))
                  :log-path (:log launched)}})))

(defn frame
  "One update frame. With a driver, presses any key due this frame. Returns
   {:qa qa :state new-state :entries [log entries] :exit? bool}: exit when the
   script is finished or the game is over."
  [qa handle state]
  (if-let [scripted (:driver qa)]
    (let [advanced (driver/advance handle scripted state)
          new-state (:state advanced)]
      {:qa (assoc qa :driver (:driver advanced))
       :state new-state
       :entries (:entries advanced)
       :exit? (boolean (or (:finished? advanced) (state/over? new-state)))})
    {:qa qa :state state :entries [] :exit? (boolean (state/over? state))}))

(defn on-key
  "A live key press. Returns {:state new-state :entries [log entries]}; the
   entries are empty unless a log was asked for and no script is driving, since
   the driver logs scripted presses itself."
  [qa handle state tick event]
  (let [pressed (play/handle-event handle state tick event)]
    (if (and (:log-path qa) (not (:driver qa)))
      pressed
      (assoc pressed :entries []))))
