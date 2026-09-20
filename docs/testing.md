# Testing

Veil uses Robert C. Martin's ("Uncle Bob") Clojure toolchain. Every command is
a `bb` task (`bb tasks` lists them); tool versions are pinned by git SHA in
`bb.edn` / `deps.edn`.

| Layer | Command | Tool | Gates? |
|---|---|---|---|
| Unit specs | `bb spec` | [speclj](https://github.com/slagyr/speclj) | yes |
| Property specs | `bb property` | [test.check](https://github.com/clojure/test.check) via speclj | never - run deliberately; see [property-testing.md](property-testing.md) |
| Spec structure | `bb scrap` | [scrap](https://github.com/unclebob/scrap) | structure errors only |
| Acceptance | `bb acceptance` | [APS](https://github.com/unclebob/Acceptance-Pipeline-Specification) | yes |
| CRAP | `bb crap`, `bb crap-gate` | [crap4clj](https://github.com/unclebob/crap4clj) | yes, `quality-gates.edn` |
| Layers | `bb layers` | [dependency-checker](https://github.com/unclebob/dependency-checker) | yes, `dependency-checker.edn` |
| Duplication | `bb dry` | [dry4clj](https://github.com/unclebob/dry4clj) | advisory |
| Mutation | `bb mutate <file>` | [clj-mutate](https://github.com/unclebob/clj-mutate) | Step 6 + weekly CI |
| Acceptance mutation | `bb acceptance-mutate` | APS | Step 6 + weekly CI |
| QA run | `bb qa <slug>`, `bb qa --all` | veil.ui.qa (in this repo) | never - local step before the playtest |
| Scripted / logged play | `bb play --keys <script> --log <file>` | veil.ui.qa | never - manual |
| Docs check | `check-clean.sh` section 7 | `veil-tools.docs-check` (in this repo, `tools/`) | advisory |
| Quil shell | `bb shell-check` | `veil-tools.shell-check` (in this repo, `tools/`) | yes, `check-clean.sh` section 9 |
| Introverted specs | `bb introvert` | [deintroverter4clj](https://github.com/unclebob/deintroverter4clj) | never - manual only |
| UML diagram | `bb uml-ir`, `bb uml` | [uml-viewer](https://github.com/unclebob/uml-viewer) | never - manual only |

`bash .claude/tools/check-clean.sh` runs every gating row in one pass
(`docs/clean-code-gate.md`).

## Unit specs (speclj)

`spec/` mirrors `src/`: `src/veil/game/menu.clj` is specified by
`spec/veil/game/menu_spec.clj` (namespace `veil.game.menu-spec`).

```
bb spec              # all specs
bb spec -a           # autotest: rerun on every save
bb spec -f d         # documentation format
```

- Spec the pure `veil.game` layer directly - no window, no Quil.
- One behavior per `it`, its description naming that behavior.
- Arrange / act / assert, visibly, in that order.
- Tag a spec `^:no-mutate` only if it must never run inside a mutation worker
  (e.g. it launches a process).

**SCRAP** (`bb scrap`) catches mis-nested forms speclj silently ignores - an
`it` inside an `it` never runs - and blocks on them. Its refactoring
recommendations are advice, not a gate.

## Property specs (test.check)

`bb property` runs the test.check properties in `spec-property/`. They are random,
so they stay out of the gate and every other run. When to run one, what makes a
good property and rerunning from a seed: [property-testing.md](property-testing.md).

## Acceptance tests (APS pipeline)

```
specs/features/<concept>.feature
  -> bb gherkin-parser (APS, pinned in tools/veil_tools/aps.clj)  build/acceptance/ir/*.json
  -> veil.acceptance.generator                                    build/acceptance/generated/
  -> clojure.test, one JVM                                        pass/fail
```

- Steps execute against **pure game state**, never the Quil window. Rendering
  is covered by the human playtest (`CLAUDE.md` Step 4.5).
- Step handlers live one namespace per concept under
  `acceptance/veil/acceptance/steps/`; register each in
  `veil.acceptance.steps/step-handlers`. A step with no handler fails loudly.
- Handlers return `(ok)`, `(fail msg)` or `(check pred msg)` from
  `veil.acceptance.step-support`.

**Acceptance mutation** (`bb acceptance-mutate`) changes each example value in
the features and reruns them. A *survivor* means an example the steps never
actually check. The mutator records a differential manifest as a comment
header at the top of each `.feature` file - commit it, so the next run only
retests changed scenarios.

## CRAP (crap4clj)

`CRAP = CC² × (1 − coverage)³ + CC`. `bb crap` runs coverage (`bb cov`) and
writes `.metrics/crap.edn`; `bb crap-gate` fails if any function exceeds
`:crap-max` in `quality-gates.edn`. A fully covered function passes up to
complexity 10; an uncovered one only up to 2 - so the fix is either specs or a
split.

## Layers (dependency-checker)

Components are the second namespace segment (`veil.ui.menu` → `:ui`).
`dependency-checker.edn` allows `:main → :ui, :game, :mods` and `:ui → :game`,
nothing else (`:game` and `:mods` depend on nothing), and fails on cycles. See `architecture.md`.

## Mutation testing (clj-mutate)

```
bb mutate src/veil/game/menu.clj --max-workers 3     # differential by default
bb mutate src/veil/game/menu.clj --scan              # count sites, run nothing
bb mutate src/veil/game/menu.clj --mutate-all        # full rerun
```

Exit 3 means surviving or uncovered mutants: a behavior no spec pins down.
Target `src/veil/game/**`, `src/veil/mods/**` and `src/veil/ui/**`, plus the
two `tools/` namespaces the coverage run instruments (`veil-tools.docs-check`,
`veil-tools.shell-check`; the `:cov` alias in `deps.edn` says why only those).
Skip the Quil shell (`veil.main`, `veil.ui.draw`: see the rule below) and the
I/O files `veil.mods.disk`, `veil.ui.font` and `veil.ui.qa.runner`. Snapshots in
`.metrics/mutate/` are committed, so later runs are differential.

## The Quil shell rule (`bb shell-check`)

Only a line that calls Quil or Processing may go uncovered, because only those
need a live applet, and it must decide nothing. Every decision and calculation
lives in a pure, specced function that the shell (`veil.main`, `veil.ui.draw`)
passes data to; which function answers what is in `docs/architecture.md`, "The
Quil shell decides nothing". The QA run (`bb qa`) and the human playtest verify
the Quil calls; neither is a gate. `veil.main` has no coverage exception:
uncovered logic there moves out.

`bb shell-check` (`tools/veil_tools/shell_check.clj`, section 9 of
`check-clean.sh`, blocking) reads the two files with the Clojure reader
(comments and strings are ignored) and exits 1, printing `<path> line <n>:
<message>`, for:

- **A guard on an expression.** `if`, `when`, `if-not` and `when-not` may test
  a symbol, a keyword lookup of one (`(:error launched)`), or a `veil.*`
  function called on such values (`(input/escape? event)`); `=`, `and`, `or`,
  `not` and any other call are expressions.
- **A calculation:** `+ - * / inc dec mod quot rem = not= < > <= >=`, called.
- **A multi-way branch:** `cond`, `case` and `condp`.
- **A missing or unreadable shell file**, so a rename can't switch the check
  off. Only `veil.main` and `veil.ui.draw` are checked.

## QA runs (deterministic keyboard QA)

A QA run plays a key script through the real game and checks what happened, so
the human playtest only has to judge what a script can't: how movement,
navigation and rendering *feel*.

```
bb qa main-menu                      # one procedure: specs/qa/main-menu.{keys,edn}
bb qa --all                          # every specs/qa/*.edn, sorted by slug
bb play --keys specs/qa/main-menu.keys --log target/qa/run.log.edn   # what qa does, by hand
bb play --log target/qa/session.log.edn                              # log a hand-played session
```

`bb qa` starts the game in a child JVM (`bb play --keys ... --log ...`, 60 s
timeout), which presses the script's keys and quits when they run out (or the
game ends), then reads the log back and checks it. Each run opens the game
window briefly.

**Files** (write both next to the `.feature`, at spec time):

- `specs/qa/<slug>.keys` - the script. One key per line: `Up` `Down` `Left`
  `Right` `Enter` `Esc` `Space`, or one letter or digit. `wait N` skips N ticks.
  `#` starts a comment; blank lines are ignored. A key takes the next tick
  (the first is tick 1); `wait N` adds N. A bad line rejects the whole script
  before any key is pressed.
- `specs/qa/<slug>.edn` - the procedure: `{:script "specs/qa/<slug>.keys"
  :expect [{...} ...]}`. Each expected map is a *partial* match against a log
  entry, matched in order; one entry satisfies at most one expectation.
- `target/qa/<slug>.log.edn` - the log (git-ignored, rewritten every run).

**Log format v1.** The first line is the header `{:log/version 1}`; every line
after it is one EDN map. Each key press logs `{:tick 3 :key :enter}`, followed
by the events that press caused, on the same tick. Scripted keys use the
script's ticks (frame numbers); a live key in `bb play --log` uses Quil's frame
count. The event vocabulary, derived by diffing game states
(`veil.game.events/between`):

| Event | Keys |
|---|---|
| `:menu/selection-changed` | `:to` - the new item as a keyword (`:options`, `:quit`) |
| `:screen/changed` | `:from`, `:to` - screens (`:main-menu`, `:map`, `:options`) |
| `:game/over` | none |

A key that changes nothing logs only the key. Events, the log version and the
key names are a contract every procedure depends on: a renamed event makes
procedures report it as missing, so change them deliberately.

**Result.** The runner prints one line per expectation (`seen` or `MISSING`),
then `QA <slug>: PASS` or `QA <slug>: FAIL (n missing)`; `--all` runs each
procedure, then prints `QA all: p passed, f failed (slugs)`. Exit 0 only when
everything passed. Problems (no such procedure, malformed script, the game
exiting non-zero or timing out, an unreadable log) go to stderr with exit 1.

**What it proves:** input to outcome through the same `veil.main` key handler
real keys use (`docs/architecture.md`, "QA runs"): the script's keys produce
the state changes the procedure expects. **What it doesn't:** rendering or feel,
and Quil's own key-event dispatch (a script builds the event map itself; it
never goes through Processing's `KeyEvent`).

**It is a local workflow step, not a gate.** It opens a window, so it is not
part of `check-clean.sh` or CI. Run `bb qa <slug>` after the coder's commit and
before the human playtest (`.claude/workflow.md`). The parsing, matching, log
format and runner decisions are covered by unit specs and by
`specs/features/deterministic-keyboard-qa.feature`; opening the window and
touching files are covered by running `bb qa`.

### Docs check (advisory)

Section 7 of `check-clean.sh` (`tools/veil_tools/docs_check.clj`) flags two
kinds of stale documentation. It only advises: each finding needs a
disposition in the completion report (`docs/clean-code-gate.md`), not a fix
to the check.

- **A `bb` task this file never mentions.** Every task in `bb.edn` (not its
  keyword entries, not `-private` ones) must appear here as `bb <task>` ending
  at a character that can't be part of a task name, so `bb qa-all` does not
  mention `qa`. A new task needs a line in this file.
- **A feature added on the branch with no QA procedure.** Only feature files
  the branch *adds* are checked (new or untracked, not merely changed). Each
  needs `specs/qa/<slug>.edn` (that file alone counts; whether its script
  exists or passes is `bb qa`'s job), or opts out with a line
  `QA: none - <reason>` in its `Feature:` description block, before the first
  `Background:` or `Scenario:`. The reason is required: `QA: none` on its own
  is still a finding, and the line means nothing after the first scenario.

## Quality gate ratchet

Loosening `quality-gates.edn` or `dependency-checker.edn` (a higher CRAP
ceiling, a new allowed layer edge, an exception, `:fail-on-*` turned off) fails
the `quality-gate-ratchet` CI job (`bb gate-ratchet`). That is deliberate: a
justified weakening is still possible, but a human must approve it explicitly.

## Build and maintenance tasks

Not test layers, but part of the `bb` surface, and the docs check requires
every task to be mentioned here.

- `bb uber` - build the runnable jar, `target/veil-<version>.jar`
  (`docs/release.md`).
- `bb clean` - delete `target/` and `build/`; the next `bb acceptance`
  regenerates `build/`.
- `bb update-tools` - repin every Uncle Bob tool (the git SHAs in `bb.edn`,
  `deps.edn` and `tools/veil_tools/aps.clj`) to its latest commit.
  `bb update-tools --check` only reports what is behind, exit 1 if anything is.
  After a real repin, rerun the gate and `bb mutate` on changed files: a new
  tool version can add findings on unchanged code.

## Manual tools

- `bb introvert` - flags specs whose assertions never touch `src/` (asserting
  on literals or test data). Its author says: inspect by hand, never gate.
- `bb single-answer` and `bb jev` - Jev spike tooling, never gates: `jev-spike.md`.
- `bb uml-ir` then `bb uml` - live UML of the namespace tree from
  `docs/uml/veil.policy.edn`, with `.metrics/` (CRAP, mutation) overlaid. Run
  `bb uml-data` first: it refreshes `crap.edn` and mutates every eligible `src`
  file (first run slow, later ones differential), then `bb uml-ir`. `:levels`
  there mirrors the layer rules, so violations draw red. The viewer's **Regen**
  button needs a Grok companion in tmux, so it doesn't work here; instead leave
  the viewer open and rerun `bb uml-ir` - it reloads on change (or press `R`).
