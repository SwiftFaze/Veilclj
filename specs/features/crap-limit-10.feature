Feature: The CRAP gate compares each function against the configured limit
  What bb crap-gate passes and fails for a given limit. CRAP is
  CC^2 x (1 - coverage)^3 + CC, so a limit sets two budgets at once: how
  complex a fully covered function may be, and how complex an untested one
  may be. Every scenario states the limit as 10, the value this change ships
  (raised from 8, where a fully covered flat cond answering one question
  failed and the cheapest fix was a split into helpers taking booleans the
  caller already knew).

  Covers: which functions the gate passes and fails against a stated limit -
    the fully covered boundary (CC 10 passes, CC 11 fails), the uncovered
    boundary (CC 2 passes, CC 3 fails), a partly covered one, the report of
    several offenders worst first, and a clean run. This is the first
    coverage of tools/veil_tools/crap_gate.clj's offenders.
  Supersedes: nothing.
  Out of scope: the value actually shipped in quality-gates.edn - every
    scenario here takes the limit as a Given, so this file passes unchanged at
    8; the quality-gate-ratchet job is what guards the shipped value, and it
    fails this change's PR on purpose for a human to approve
    (tools/veil_tools/gate_ratchet.clj, unchanged and not specified here); the
    judgment line banning boolean-flag splits (docs/clean-code-gate.md - a
    human reads it, no check can); a mechanical exception for "one cond/case"
    (crap4clj cannot tell one question from several); every other threshold
    (mutation, SCRAP, dependency rules); the docs/testing.md wording fix; how
    crap4clj computes CC and coverage; reading quality-gates.edn and
    .metrics/crap.edn from disk, which is the bb task's I/O, covered by
    running the gate.
  QA: none - a quality gate has no keyboard-driven behavior.

  # Which functions pass at the limit

  Scenario: A fully covered function at the limit passes
    Given the CRAP limit is 10
    And a function with complexity 10 and 100% coverage
    When the CRAP gate runs
    Then it passes

  Scenario: A fully covered function one over the limit fails
    Given the CRAP limit is 10
    And a function with complexity 11 and 100% coverage
    When the CRAP gate runs
    Then it fails
    And the failure names that function

  Scenario Outline: Coverage buys complexity
    Given the CRAP limit is 10
    And a function with complexity <complexity> and <coverage>% coverage
    When the CRAP gate runs
    Then the CRAP score is <crap>
    And it <verdict>

    Examples:
      | complexity | coverage | crap   | verdict |
      | 2          | 0        | 6      | passes  |
      | 3          | 0        | 12     | fails   |
      | 5          | 50       | 8.125  | passes  |
      | 6          | 50       | 10.5   | fails   |

  # What the gate reports

  Scenario: Every offender is reported, worst first
    Given the CRAP limit is 10
    And a function with complexity 11 and 100% coverage
    And a function with complexity 20 and 100% coverage
    When the CRAP gate runs
    Then it fails
    And it reports 2 offenders, worst first

  Scenario: Nothing over the limit passes
    Given the CRAP limit is 10
    And a function with complexity 10 and 100% coverage
    And a function with complexity 2 and 0% coverage
    When the CRAP gate runs
    Then it passes

  # ---------------------------------------------------------------------------
  # Non-goals
  #   - Pinning the shipped :crap-max. A scenario reading the real
  #     quality-gates.edn would be a change-detector the same commit could
  #     edit, and would be the repo's first disk-reading step; the ratchet
  #     guards the value with a human in the loop instead.
  #   - Letting a single cond/case through mechanically.
  #   - Changing tools/veil_tools/crap_gate.clj or gate_ratchet.clj.
  #   - Specifying the ratchet's 8 -> 10 refusal: a different concept, so a
  #     different feature file if it ever gets one.
  #
  # Risks
  #   - This file does not test the change it ships with. Reverting
  #     quality-gates.edn to 8 leaves every scenario green; only the
  #     quality-gate-ratchet job (and the PR reviewer) notice a value change.
  #   - The Given steps must compute :crap with crap4clj.crap/crap-score, the
  #     same function the real pipeline uses, not a reimplemented formula, or
  #     the scenarios could agree with themselves and disagree with the gate.
  #   - The gate skips entries whose :crap is nil (no coverage data):
  #     `(some-> (:crap %) (> crap-max))` never flags them. Existing
  #     behavior, unchanged and not specified here; a function crap4clj
  #     could not measure passes the gate silently.
  #   - Raising a ceiling is one-way in practice: functions grow into the new
  #     headroom. The judgment line in docs/clean-code-gate.md is what keeps
  #     10 a ceiling rather than a target.
  #
  # Open questions
  #   - None.
  # ---------------------------------------------------------------------------
