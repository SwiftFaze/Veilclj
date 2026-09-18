# CLAUDE.md

Veil is a 2D ASCII-tile desktop RPG built with Java 17 Swing (no game engine).
Rendering draws Unicode/ASCII glyphs with `Graphics2D.drawString` onto a
`JPanel`; there is no sprite/texture pipeline for tiles.

## Editing this file

<!-- added 2026-09-06: these files had begun accumulating one-off fixes with no
     removal pressure; see docs/instruction-files.md for the full rationale. -->

Rules here load into context on every task, so a bad one costs more than a bad
line of code. Before adding:

- **Only add what will matter across many future tasks.** A one-off mistake gets
  fixed in the code or that PR — a standing instruction is the most expensive
  possible fix for it.
- **State, not rules, belongs in the issue.** "X is broken until #N lands" rots
  the moment #N lands, and nothing prompts anyone to remove it.
- **Tag every rule** `<!-- added YYYY-MM-DD: why -->`. Without the reason, nobody
  can later tell a load-bearing rule from a dead one, so nothing is ever deleted.
- **One canonical home per rule.** Other files link to it, never restate it.
- **If it is mechanically checkable, write the check, not the rule.**
- **At or over budget, adding requires removing.** Budgets are CI-enforced:
  `bash .claude/tools/check-instruction-budget.sh`.

Re-audit monthly — checklist in `docs/instruction-files.md`.

## Never do these

- **Never commit or push directly to `master`.** Only two PRs may target it:
  from `develop` (release promotion) or from `hotfix/*`. Enforced by branch
  protection (`enforce_admins`) and the `master-source-check` CI job.
- **Never merge a feature/fix/docs PR into `develop` with a merge commit** —
  always `gh pr merge <n> --squash --delete-branch`. A merge commit duplicates
  every `CHANGELOG.md` entry (see `docs/release.md`). The one exception is the
  `develop` → `master` promotion PR, which must stay a true merge
  (`gh pr merge --merge`) — see the `close-milestone` skill.
- **Never hand-edit `pom.xml`'s `<version>` or `CHANGELOG.md`.** Release Please
  derives both from Conventional Commits — `docs/release.md`.
- **Always put `Closes #N` in the PR body.** GitHub's own auto-close never fires
  here (PRs merge into `develop`, not the default branch), so
  `.github/workflows/close-linked-issues.yml` does it on merge instead — but
  only if the reference is there. CI-enforced by `repo-hygiene.yml`.

## Build & run

- `mvn compile` — build. `mvn test` — unit + Cucumber acceptance. `mvn verify` —
  everything, including integration tests.
- Single test: `mvn test -Dtest=PlayerTest#movingRightIncreasesX` /
  `mvn test -Dcucumber.filter.name="A newly created player starts as a Warrior"` /
  `mvn verify -Dit.test=ModLoaderIT`.
- Run the game: `mvn compile exec:java`. Add `-Dveil.devConsole=true` for the
  F1 dev console (live player-stat editing).
- Run the class/stats sandbox: `mvn compile exec:java -Dexec.mainClass=com.swiftfaze.veil.sandbox.ClassSandbox`.
- Re-approve changed approval-test fixtures: `mvn compile exec:java -Dexec.mainClass=com.swiftfaze.veil.testing.approval.ApprovalReapprove`.
- Mutation testing: `mvn org.pitest:pitest-maven:mutationCoverage` → `target/pit-reports/`.
- `mvn package` → `target/Veil-<version>-app.jar` (runnable fat jar).
- Test-layer rationale and troubleshooting: `docs/testing.md`.

## Docs

`docs/README.md` is the index. Read before writing code in these areas:

- Any Swing panel/widget → `docs/ui-styling.md` (padding, type scale, and the
  rule that a color is a `WidgetTheme` key, never a literal).
- Any test work → `docs/testing.md`.
- Engine/data model → `docs/architecture.md`.
- Player-visible game data (class base stats, attributes, combat formulas) →
  also update the [GitHub wiki](https://github.com/SwiftFaze/Veil/wiki) in the
  same change; see `docs/wiki.md`.

## Spec-first workflow

`.claude/workflow.md` has the pipeline. Read it when starting or resuming a
pipeline step, not every session. Repo-specific file layout:

- `specs/intent/<slug>.md` — gitignored scratch; copy `TEMPLATE.md`, or use the
  `spec-intent` skill to derive one from a GitHub issue.
- `specs/features/<slug>.feature` — run by Cucumber via `RunCucumberTest`.
  **One `.feature` file per distinct concept**, never a bundled file. Each
  file's `Feature:` description block is the single source of truth for what it
  covers, supersedes, and excludes — keep that current, not a separate index.

Two repo-specific pipeline steps, both mandatory:

- **Step 4.5 — human playtest.** After implementation, before acceptance tests:
  the human runs `mvn compile exec:java` and plays the changed behavior. Tests
  prove the code does what the spec says, not whether movement, navigation, or
  rendering *feel* right. For a multi-area change, playtest each area as it
  lands, not once at the end. Record what was tested in the PR description. No
  feature is done without this.
- **Step 7.5 — close the linked issue.** Automatic on merge via `Closes #N`
  above. Still manual for an issue with no PR of its own: `gh issue close <n>
  --repo SwiftFaze/Veil --reason completed`, and set the VEIL board's `Status`
  to Done if it doesn't follow on its own.
