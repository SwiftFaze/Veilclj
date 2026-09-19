Feature: Advisory docs check
  An advisory check in the Clean Code gate that catches two kinds of stale
  docs mechanically, so the implement-issue skill's docs verification isn't
  the only thing standing between a change and a stale doc: a bb task that
  docs/testing.md never mentions, and a feature added on the branch with no
  QA procedure and no stated reason for having none.

  Covers: which bb.edn entries count as tasks (the ones `bb tasks` lists),
    when docs/testing.md counts as mentioning a task ("bb <task>" ending at a
    character that can't be part of a task name), which feature files the
    QA-procedure check looks at (only those added on the branch), what counts
    as a matching procedure (specs/qa/<slug>.edn alone), the
    "QA: none - <reason>" opt-out line (in the Feature: description block,
    reason required), and the findings reported for each miss.
  Supersedes: nothing.
  Out of scope: the implement-issue skill itself, the pointer edits and the
    hardener reading-scope line (markdown, verified by the done criteria in
    specs/intent/implement-issue-skill.md, not by scenarios); whether a QA
    procedure's script exists or passes (bb qa's job); making either finding
    blocking (a separate gate-scope change). Reading bb.edn, docs/testing.md
    and the feature files from disk, working out the branch's added files
    from git, and printing the findings are the check-clean.sh wrapper's I/O,
    covered by running the gate, not by these steps.
  QA: none - the check has no keyboard-driven behavior.

  # Every bb task is mentioned in docs/testing.md

  Scenario: No findings when docs/testing.md mentions every task
    Given bb.edn defines the tasks "play", "spec" and "qa"
    And docs/testing.md mentions "bb play", "bb spec" and "bb qa <slug>"
    When the docs check runs
    Then it reports no findings

  Scenario: A task docs/testing.md never mentions is a finding
    Given bb.edn defines the tasks "play" and "uber"
    And docs/testing.md mentions "bb play" only
    When the docs check runs
    Then it reports 1 finding
    And the finding says task "uber" is not mentioned in docs/testing.md

  Scenario: Every unmentioned task is its own finding
    Given bb.edn defines the tasks "play", "uber", "clean" and "update-tools"
    And docs/testing.md mentions "bb play" only
    When the docs check runs
    Then it reports findings for the tasks "clean", "uber" and "update-tools", in that order

  Scenario Outline: What counts as mentioning a task
    Given bb.edn defines the task "qa"
    And docs/testing.md contains the line "<line>"
    When the docs check runs
    Then task "qa" is <verdict>

    Examples:
      | line                            | verdict       |
      | Run bb qa <slug> first.         | mentioned     |
      | Run `bb qa` first.              | mentioned     |
      | Finish with bb qa.              | mentioned     |
      | bb qa                           | mentioned     |
      | Run bb qa-all first.            | not mentioned |
      | Run bb qa2 first.               | not mentioned |
      | The qa step runs before play.   | not mentioned |

  Scenario: Keyword entries and private tasks are not checked
    Given bb.edn's tasks map has the keys ":init", ":requires", "-helper" and "play"
    And docs/testing.md mentions "bb play" only
    When the docs check runs
    Then it reports no findings

  # A feature added on the branch has a QA procedure or says why not

  Scenario: An added feature with a procedure is fine
    Given the branch adds the feature file "specs/features/door.feature"
    And the procedure "specs/qa/door.edn" exists
    And no key script "specs/qa/door.keys" exists
    When the docs check runs
    Then it reports no findings

  Scenario: An added feature with the opt-out line is fine
    Given the branch adds the feature file "specs/features/save-format.feature"
    And its Feature: description block has the line "QA: none - no key input involved"
    And no procedure "specs/qa/save-format.edn" exists
    When the docs check runs
    Then it reports no findings

  Scenario: An added feature with neither is a finding
    Given the branch adds the feature file "specs/features/door.feature"
    And its Feature: description block has no "QA: none" line
    And no procedure "specs/qa/door.edn" exists
    When the docs check runs
    Then it reports 1 finding
    And the finding says feature "door" has no QA procedure and no "QA: none" line

  Scenario Outline: An opt-out with no reason is a finding
    Given the branch adds the feature file "specs/features/door.feature"
    And its Feature: description block has the line "<line>"
    And no procedure "specs/qa/door.edn" exists
    When the docs check runs
    Then it reports 1 finding
    And the finding says feature "door" has a "QA: none" opt-out with no reason

    Examples:
      | line        |
      | QA: none    |
      | QA: none -  |

  Scenario: An opt-out line outside the description block doesn't count
    Given the branch adds the feature file "specs/features/door.feature"
    And the line "QA: none - no key input involved" appears only after its first Scenario
    And no procedure "specs/qa/door.edn" exists
    When the docs check runs
    Then it reports 1 finding
    And the finding says feature "door" has no QA procedure and no "QA: none" line

  Scenario: Features the branch didn't add are not checked
    Given the feature file "specs/features/old.feature" already exists on develop
    And the branch changes it
    And no procedure "specs/qa/old.edn" exists
    When the docs check runs
    Then it reports no findings

# Non-goals:
#   - Checking the skill's own behavior (sequence, playtest stop, PR timing).
#     An LLM session can't be driven by acceptance steps; done criteria cover it.
#   - Checking that docs/testing.md *describes* a task well, only that it
#     mentions it.
#   - Checking a procedure's :script file (bb qa already errors on it).
#   - Blocking the gate. Both findings are advisory.
#
# Risks:
#   - The check lives in tools/veil_tools/docs_check.clj, and "tools" joins the
#     :spec and :acceptance classpaths. It must stay plain Clojure (no
#     babashka-only APIs) to load on the JVM. The existing untested veil_tools
#     namespaces become visible to bb spec / bb dry but stay unspecced here.
#   - The first run after this lands must be clean: uber, clean and
#     update-tools get documented in docs/testing.md in this same PR.
#
# Open questions:
#   - None; settled in the intent doc's Clarifications.
