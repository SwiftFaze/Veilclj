Feature: Deterministic keyboard QA run
  A key script is played through the game's normal key path, every key press
  and the game events it causes are written to a log, and a per-feature QA
  procedure checks that log for expected entries. The human playtest then only
  has to judge what a script can't: how movement, navigation and rendering feel.

  Covers: parsing key scripts (key lines, waits, comments, errors), how script
    keys reach the game, the events the game reports for the existing screens,
    the shape, version line and tick numbering of the log, the launch flags (--keys,
    --log), and checking a log against a procedure's expected entries
    (matching, order, seen/missing report, exit status).
  Supersedes: nothing.
  Out of scope: seeded randomness (--seed; the game draws no random numbers
    yet), pixel or screenshot comparison, mouse input, and how any screen is
    drawn. Opening the real window, quitting the process when the script ends
    and reading files from disk are I/O in veil.main and are covered by the
    human playtest and a manual `bb qa` run, not by these steps.

  Background:
    Given the game has just started

  Scenario Outline: A key line names the key to press
    When the key script line "<line>" is parsed
    Then the script presses <key>

    Examples:
      | line  | key   |
      | Up    | Up    |
      | Down  | Down  |
      | Left  | Left  |
      | Right | Right |
      | Enter | Enter |
      | Esc   | Esc   |
      | Space | Space |
      | W     | W     |
      | 7     | 7     |

  Scenario Outline: Blank lines and comments press nothing
    When the key script "<script>" is parsed
    Then the script presses <keys>

    Examples:
      | script                     | keys       |
      |                            | no keys    |
      | # open Options             | no keys    |
      | Down # move to Options     | Down       |
      | Down / / # gap / Enter     | Down Enter |

  Scenario Outline: Keys take consecutive ticks and a wait skips ticks
    When the key script "<script>" is parsed
    Then the keys are pressed on ticks <ticks>

    Examples:
      | script                | ticks |
      | Down                  | 1     |
      | Down / Enter          | 1 2   |
      | Down / wait 3 / Enter | 1 5   |
      | Down / wait 0 / Enter | 1 2   |
      | wait 2 / Down         | 3     |

  Scenario Outline: A malformed script is rejected before any key is played
    When the key script "<script>" is parsed
    Then the script is rejected with "<error>"

    Examples:
      | script                | error                              |
      | Bogus                 | line 1: unknown key "Bogus"        |
      | Down / Hyper          | line 2: unknown key "Hyper"        |
      | wait                  | line 1: wait needs a whole number  |
      | wait abc              | line 1: wait needs a whole number  |
      | Down / wait -1        | line 2: wait needs a whole number  |
      | Down / wait 1.5       | line 2: wait needs a whole number  |

  Scenario Outline: A script key reaches the game as the same input a real key press gives
    When the script presses <key>
    Then the game input is <input>

    Examples:
      | key   | input    |
      | Up    | up       |
      | W     | up       |
      | Down  | down     |
      | S     | down     |
      | Enter | confirm  |
      | Esc   | back     |
      | X     | no input |
      | Space | no input |
      | Left  | no input |

  Scenario Outline: Playing a script drives the game and ends the run
    When the script "<script>" is played
    Then the current screen is <screen>
    And the game is <over>
    And the run is finished

    Examples:
      | script                  | screen    | over     |
      | Down / Enter            | options   | not over |
      | Down / Enter / Esc      | main menu | not over |
      | Enter                   | map       | not over |
      | Up / Enter              | main menu | over     |

  Scenario Outline: Each key press is logged, followed by the events it caused
    When the script "<script>" is played
    Then the log entries are "<log>"

    Examples:
      | script     | log                                                                                                                                                                       |
      | Down       | {:tick 1 :key :down} / {:tick 1 :event :menu/selection-changed :to :options}                                                                                              |
      | Up         | {:tick 1 :key :up} / {:tick 1 :event :menu/selection-changed :to :quit}                                                                                                   |
      | Enter      | {:tick 1 :key :enter} / {:tick 1 :event :screen/changed :from :main-menu :to :map}                                                                                         |
      | Up / Enter | {:tick 1 :key :up} / {:tick 1 :event :menu/selection-changed :to :quit} / {:tick 2 :key :enter} / {:tick 2 :event :game/over}                                              |
      | Enter / Esc | {:tick 1 :key :enter} / {:tick 1 :event :screen/changed :from :main-menu :to :map} / {:tick 2 :key :esc} / {:tick 2 :event :screen/changed :from :map :to :main-menu} |

  Scenario Outline: A key that changes nothing logs only the key
    When the script "<script>" is played
    Then the log entries are "<log>"

    Examples:
      | script | log                |
      | X      | {:tick 1 :key :x}     |
      | Left   | {:tick 1 :key :left}  |
      | Space  | {:tick 1 :key :space} |
      | Esc    | {:tick 1 :key :esc}   |

  Scenario: A wait shows up as a gap in the log's ticks
    When the script "Down / wait 4 / Down" is played
    Then the log entries are "{:tick 1 :key :down} / {:tick 1 :event :menu/selection-changed :to :options} / {:tick 6 :key :down} / {:tick 6 :event :menu/selection-changed :to :quit}"

  Scenario: The log starts with its format version
    When the script "Down" is played
    Then the first log line is {:log/version 1}
    And the log entries follow it

  Scenario: The log is one EDN map per line
    When the script "Down" is played
    Then the log text has 3 lines
    And every log line reads back as one EDN map

  Scenario Outline: Launch flags choose scripted input and logging
    When the launch arguments are "<args>"
    Then the key script is <keys>
    And the log file is <log>

    Examples:
      | args                                       | keys                     | log               |
      |                                            | none                     | none              |
      | --keys specs/qa/main-menu.keys             | specs/qa/main-menu.keys  | none              |
      | --log target/qa/run.edn                    | none                     | target/qa/run.edn |
      | --keys a.keys --log target/qa/run.edn      | a.keys                   | target/qa/run.edn |

  Scenario: Without flags the game runs as a normal interactive session
    When the launch arguments are ""
    Then no script is played and no log is written

  Scenario Outline: Bad launch arguments are rejected
    When the launch arguments are "<args>"
    Then the launch is rejected with "<error>"

    Examples:
      | args           | error                     |
      | --keys         | --keys needs a file path  |
      | --log          | --log needs a file path   |
      | --bogus        | unknown argument --bogus  |
      | --keys a --log | --log needs a file path   |

  Scenario Outline: A procedure passes when its expected entries appear in order
    Given the script "<script>" has been played
    When the procedure expects "<expected>"
    Then the report marks the expected entries as <report>
    And the QA run exits with status <status>

    Examples:
      | script       | expected                                                                                       | report        | status |
      | Down         | {:event :menu/selection-changed :to :options}                                                  | seen          | 0      |
      | Down / Enter | {:key :down} / {:event :screen/changed :from :main-menu :to :options}                          | seen seen     | 0      |
      | Down / Enter | {:event :screen/changed :to :options}                                                          | seen          | 0      |
      | Down / Enter | {:tick 2 :key :enter}                                                                          | seen          | 0      |
      | Down         | {:event :menu/selection-changed :to :quit}                                                     | missing       | 1      |
      | Down / Enter | {:tick 3 :key :enter}                                                                          | missing       | 1      |
      | Down / Enter | {:event :screen/changed :to :options} / {:event :menu/selection-changed :to :options}          | seen missing  | 1      |
      | Down         | {:key :down} / {:key :down}                                                                    | seen missing  | 1      |

  Scenario Outline: The runner rejects a log it cannot read
    Given the log text is "<log>"
    When the procedure expects "{:key :down}"
    Then the check is rejected with "<error>"
    And the QA run exits with status 1

    Examples:
      | log                                        | error                                                 |
      | {:log/version 2}                           | unsupported log version 2 (this runner reads 1)       |
      | {:tick 1 :key :down}                       | log has no version line                               |
      |                                            | log has no version line                               |

  Scenario: A procedure that expects nothing is rejected
    When the procedure expects ""
    Then the procedure is rejected with "expects nothing"
    And the QA run exits with status 1

  Scenario Outline: A procedure file must name its key script and its expected entries
    When the procedure file reads "<edn>"
    Then the procedure is rejected with "<error>"

    Examples:
      | edn                                                       | error                          |
      | {:expect [{:key :down}]}                                  | missing :script                |
      | {:script "specs/qa/x.keys"}                               | missing :expect                |
      | {:script "specs/qa/x.keys" :expect []}                    | expects nothing                |

  Scenario: A QA slug with no procedure is reported by its expected path
    When the QA slug is "no-such-feature"
    Then the QA run is rejected with "no QA procedure for no-such-feature (expected specs/qa/no-such-feature.edn)"
    And the QA run exits with status 1

# Non-goals:
#   - Seeded randomness (--seed). The game draws no random numbers yet; the flag
#     ships with the first feature that does (expected: world generation).
#   - Making `bb qa` part of check-clean.sh / CI. It is a workflow step run
#     before the human playtest; promoting it to a gate is a separate
#     high-risk change (it alters a quality gate's scope).
#   - Pixel/screenshot comparison; mouse input.
#   - Replacing the human playtest (CLAUDE.md Step 4.5) - it gets narrower.
#
# Risks:
#   - These steps prove parsing, event derivation, log shape and matching on
#     pure data. They do NOT prove the script goes through Quil's real key path
#     (Processing's KeyEvent handling, the Esc/exit hack in veil.main), that the
#     window closes when the script ends, or that files are read/written. Those
#     are veil.main I/O, covered by a manual `bb qa main-menu` run and the
#     playtest.
#   - The event vocabulary (:menu/selection-changed, :screen/changed,
#     :game/over) and the log version become a contract every QA procedure
#     depends on. Renaming an event breaks procedures unless the runner reports
#     it as missing.
#   - Changing veil.game's input handler to report events is a contract change
#     for every caller (veil.main, main-menu acceptance steps, unit specs).
#   - The "Playing a script drives the game" and "script key reaches the game"
#     scenarios overlap main-menu.feature on purpose: they pin that the SCRIPT
#     path uses the same translation, not that the menu itself is right.
#   - Scenario tables use " / " as the line separator and "no keys" / "none"
#     placeholders; step handlers must agree on those spellings.
#   - A missing key-script file or an unwritable log path is reported by
#     veil.main I/O and is not covered by these steps.
#
# Open questions: none.
