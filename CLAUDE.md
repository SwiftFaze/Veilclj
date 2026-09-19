# CLAUDE.md

Veil is a 2D ASCII-tile desktop RPG in Clojure 1.12, rendered with Quil
(Processing). Every tile is a glyph drawn as text; there is no sprite/texture
pipeline. Game rules are pure functions over one state map — layering in
`docs/architecture.md`.

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
  from `develop` (release promotion) or from `hotfix/*`. Enforced by the
  `master-source-check` CI job.
- **Never merge a feature/fix/docs PR into `develop` with a merge commit** —
  always `gh pr merge <n> --squash --delete-branch`. A merge commit duplicates
  every `CHANGELOG.md` entry (see `docs/release.md`). The one exception is the
  `develop` → `master` promotion PR, which must stay a true merge
  (`gh pr merge --merge`) — see the `close-milestone` skill.
- **Never hand-edit `version.txt` or `CHANGELOG.md`.** Release Please derives
  both from Conventional Commits — `docs/release.md`.
- **Always put `Closes #N` in the PR body.** GitHub's own auto-close never fires
  here (PRs merge into `develop`, not the default branch), so
  `.github/workflows/close-linked-issues.yml` does it on merge instead — but
  only if the reference is there. CI-enforced by `repo-hygiene.yml`.

## Build & run

<!-- added 2026-09-18: bb, not clj, because the scoop `clj` is a PowerShell
     module that Git Bash can't call; bb's built-in launcher works everywhere. -->
Always go through `bb` tasks (`bb.edn`), never a raw `clj`/`clojure` call.

- `bb play` — run the game. `bb spec` — unit specs (`bb spec -a` autotest).
- `bb acceptance` — `specs/features/*.feature` via Uncle Bob's APS pipeline.
- `bash .claude/tools/check-clean.sh` — the full gate (specs, SCRAP,
  acceptance, CRAP, layers, duplication, docs check, text smells).
- `bb mutate <file>`, `bb acceptance-mutate` — mutation testing (Step 6).
- `bb uber` → `target/veil-<version>.jar`. `bb tasks` lists everything;
  every tool is described in `docs/testing.md`.

## Docs

`docs/README.md` is the index. Read before writing code in these areas:

- Adding a namespace, or unsure where code goes → `docs/architecture.md`.
- Any spec work → `docs/testing.md`.

## Spec-first workflow

`.claude/workflow.md` has the pipeline. Read it when starting or resuming a
pipeline step, not every session. Repo-specific file layout:

- `specs/intent/<slug>.md` — gitignored scratch; copy `TEMPLATE.md`, or use the
  `spec-intent` skill to derive one from a GitHub issue.
- `specs/features/<slug>.feature` — run by the acceptance pipeline
  (`docs/testing.md`). **One `.feature` file per distinct concept**, never a
  bundled file. Each file's `Feature:` description block is the single source
  of truth for what it covers, supersedes, and excludes.

Two repo-specific pipeline steps, both mandatory:

<!-- added 2026-09-19: coder does Steps 4-5 in one run; hardener can't change behavior (#23); `bb qa` precedes the playtest and narrows it (#24) -->
- **Step 4.5 — human playtest.** After the coder's commit and a passing `bb qa
  <slug>` (`.claude/workflow.md`), before the hardener: the human runs
  `bb play` and plays the changed behavior. Tests prove the code
  does what the spec says, not whether movement, navigation, or rendering
  *feel* right. For a multi-area change, playtest each area as it lands, not
  once at the end. Record what was tested in the PR description. No feature is
  done without this.
- **Step 7.5 — close the linked issue.** Automatic on merge via `Closes #N`
  above. Still manual for an issue with no PR of its own: `gh issue close <n>
  --repo SwiftFaze/Veilclj --reason completed`, and set the VEILCLJ board's
  (project 3) `Status` to Done if it doesn't follow on its own.
