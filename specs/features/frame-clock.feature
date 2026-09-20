# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-09-20T09:08:22.984676900Z","feature_name":"Frame clock","feature_path":"specs/features/frame-clock.feature","background_hash":"74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b","implementation_hash":"unknown","scenarios":[]}
# acceptance-mutation-manifest-end

Feature: Frame clock
  The game state carries the current time as :now-ms, stamped once per frame
  by the Quil shell. Everything that animates - a spinner, a toast, a fade -
  reads that one number, so everything in a frame agrees about what time it
  is and nothing in the pure layers ever reads a clock itself.

  Stamping is a pure function of a state and a timestamp, so a spec drives
  time by passing fixed numbers rather than by waiting.

  Covers: stamping a timestamp into a state, a later timestamp replacing an
    earlier one, stamping leaving the rest of the state alone, the same state
    and timestamp always giving the same result, and a state that has never
    been stamped carrying no time until it is.
  Supersedes: nothing. No field of the state was time-derived before this.
  Out of scope: every reader of :now-ms - spinners are issue #16 and toasts
    are issue #15, and this file only puts the number in the state; timers,
    scheduling and anything that fires when a deadline passes; wall-clock
    dates; which clock the shell reads and the reading itself, which needs a
    live window; and the input vocabulary and dispatch chain, which are the
    same issue but a separate concept (keyboard-input.feature).
  QA: none - a stamped number changes no screen and no menu selection, and
    the QA log only records events derived from a state difference, so a key
    script would show nothing. That the shell reads the clock every frame is
    the human playtest's to see, in a widget that animates (issues #15, #16).

  Scenario: Stamping a timestamp puts it in the state
    Given the game has just started
    When the frame is stamped at 1000 ms
    Then the state's time is 1000 ms

  Scenario: A later timestamp replaces an earlier one
    Given the game has just started
    And the frame is stamped at 1000 ms
    When the frame is stamped at 1016 ms
    Then the state's time is 1016 ms

  Scenario: Stamping changes nothing else in the state
    Given the game has just started
    And the selected menu item is Options
    When the frame is stamped at 1000 ms
    Then the selected menu item is Options
    And the current screen is the main menu
    And the game is not over
    And the state differs from before only in its time

  Scenario Outline: The same state and timestamp always give the same result
    Given the game has just started
    When the frame is stamped at <ms> ms twice over
    Then both results are the same state
    And the state's time is <ms> ms

    Examples:
      | ms      |
      | 0       |
      | 1       |
      | 16      |
      | 1000000 |

  Scenario: A state that has never been stamped carries no time
    Given the game has just started
    Then the state has no time

  Scenario: Stamping a state that has never been stamped gives it one
    Given the game has just started
    And the state has no time
    When the frame is stamped at 42 ms
    Then the state's time is 42 ms

# Non-goals:
#   - Anything that reads :now-ms. Spinners (#16), toasts (#15) and fades all
#     come later; this feature only guarantees the number is there and fresh.
#   - Timers, deadlines, scheduling, and wall-clock dates.
#
# Risks:
#   - That the shell actually stamps every frame is not provable here. These
#     scenarios drive the pure function directly with fixed numbers; the wiring
#     into Quil's update callback needs a live window, so it belongs to the
#     human playtest, exactly as the Esc/Processing workaround does.
#   - :now-ms is milliseconds since the sketch started, not wall-clock time.
#     Nothing here would fail if the shell passed wall-clock milliseconds
#     instead, because both are just numbers to the pure function. The choice
#     is recorded in specs/intent/frame-clock.md and is the playtest's to see.
#
# Open questions: none.
