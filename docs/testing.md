# Testing

Veil uses Robert C. Martin's ("Uncle Bob") Clojure toolchain. Every command is
a `bb` task (`bb tasks` lists them); tool versions are pinned by git SHA in
`bb.edn` / `deps.edn`.

| Layer | Command | Tool | Gates? |
|---|---|---|---|
| Unit specs | `bb spec` | [speclj](https://github.com/slagyr/speclj) | yes |
| Spec structure | `bb scrap` | [scrap](https://github.com/unclebob/scrap) | structure errors only |
| Acceptance | `bb acceptance` | [APS](https://github.com/unclebob/Acceptance-Pipeline-Specification) | yes |
| CRAP | `bb crap`, `bb crap-gate` | [crap4clj](https://github.com/unclebob/crap4clj) | yes, `quality-gates.edn` |
| Layers | `bb layers` | [dependency-checker](https://github.com/unclebob/dependency-checker) | yes, `dependency-checker.edn` |
| Duplication | `bb dry` | [dry4clj](https://github.com/unclebob/dry4clj) | advisory |
| Mutation | `bb mutate <file>` | [clj-mutate](https://github.com/unclebob/clj-mutate) | Step 6 + weekly CI |
| Acceptance mutation | `bb acceptance-mutate` | APS | Step 6 + weekly CI |
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
`:crap-max` in `quality-gates.edn` (8). A fully covered function passes up to
complexity 8; an uncovered one only up to 2 - so the fix is either specs or a
split.

## Layers (dependency-checker)

Components are the second namespace segment (`veil.ui.menu` → `:ui`).
`dependency-checker.edn` allows `:main → :ui, :game` and `:ui → :game`, nothing
else, and fails on cycles. See `architecture.md`.

## Mutation testing (clj-mutate)

```
bb mutate src/veil/game/menu.clj --max-workers 3     # differential by default
bb mutate src/veil/game/menu.clj --scan              # count sites, run nothing
bb mutate src/veil/game/menu.clj --mutate-all        # full rerun
```

Exit 3 means surviving or uncovered mutants: a behavior no spec pins down.
Target `src/veil/game/**` and `src/veil/ui/**`; `veil.main` is the Quil shell,
which specs never execute, so its mutants are uncoverable by design. Snapshots
in `.metrics/mutate/` are committed so later runs only retest changed forms.

## Quality gate ratchet

Loosening `quality-gates.edn` or `dependency-checker.edn` (a higher CRAP
ceiling, a new allowed layer edge, an exception, `:fail-on-*` turned off) fails
the `quality-gate-ratchet` CI job (`bb gate-ratchet`). That is deliberate: a
justified weakening is still possible, but a human must approve it explicitly.

## Manual tools

- `bb introvert` - flags specs whose assertions never touch `src/` (asserting
  on literals or test data). Its author says: inspect by hand, never gate.
- `bb uml-ir` then `bb uml` - live UML of the namespace tree from
  `docs/uml/veil.policy.edn`, with `.metrics/` (CRAP, mutation) overlaid. Run
  `bb uml-data` first: it refreshes `crap.edn` and mutates every eligible
  `src` file (a first run is slow, later ones are differential), then runs
  `bb uml-ir`. `:levels` there mirrors the layer rules, so
  violations draw red. The viewer's **Regen** button needs a Grok companion
  in tmux, so it doesn't work here; instead leave the viewer open and rerun
  `bb uml-ir` - it reloads the diagram when the file changes (or press `R`).
