# Veil

Veil is a 2D ASCII-style open-world RPG, written in Clojure and rendered with
[Quil](http://quil.info). It's a port of the Java/Swing
[Veil](https://github.com/SwiftFaze/Veil), rebuilt around Robert C. Martin's
("Uncle Bob") test-first workflow and tooling.

## Getting the game

Grab the latest installer for your platform from the
[Releases page](https://github.com/SwiftFaze/Veilclj/releases) —
Windows (`.exe`), Linux (`.deb`), and macOS (`.pkg`, unsigned — right-click
→ Open past the first Gatekeeper warning). Prereleases tagged `-beta.N` are
newer, less stable builds; anything without a beta tag is the current stable
release.

## Running from source

Requires a JDK 21+ and [Babashka](https://babashka.org). Babashka runs the
project's `deps.edn` aliases itself, so the Clojure CLI is optional.

```
bb play        # run the game
bb spec        # unit specs (bb spec -a to rerun on save)
bb cov         # coverage report -> target/coverage/
bb uber        # runnable jar -> target/veil-<version>.jar
bb tasks       # list every task
```

On Windows, `scoop install babashka` (from the `scoop-clojure` bucket) is the
easiest route.

## Project layout

```
src/veil/        game source: main (entry) -> ui (Quil drawing) -> game (pure rules)
spec/veil/       speclj unit specs, mirroring src/
specs/features/  Gherkin acceptance specs
specs/intent/    intent-doc template (the docs themselves are local scratch)
docs/            narrative documentation, indexed by docs/README.md
```

## Documentation

- [`CLAUDE.md`](CLAUDE.md) — project conventions and guidance for working in this repo with Claude Code
- [`docs/architecture.md`](docs/architecture.md) — functional core / imperative shell, and the layer rules
- [`docs/testing.md`](docs/testing.md) — speclj, coverage, and the Uncle Bob tooling
- [`docs/clean-code-gate.md`](docs/clean-code-gate.md) — the `check-clean.sh` gate every change passes
- [`docs/release.md`](docs/release.md) — versioning, changelog generation, and how releases are built

## Adding a feature or fix (spec-first workflow)

This repo builds every non-trivial change through an intent → spec →
implementation pipeline, automated with
[Claude Code](https://claude.com/claude-code) skills committed under
`.claude/skills/`. Full policy (model per step, checkpointing,
constraints) lives in `.claude/workflow.md`; the short version:

| Step | What happens | How |
|---|---|---|
| 1. Get the idea into a GitHub issue | Already have one? Skip to step 2. Just want to think out loud and file it for later, not build it now? | Ask Claude to use the **`brainstorm-issue`** skill — it scopes the idea with you and files it on the [VEILCLJ project board](https://github.com/users/SwiftFaze/projects/3) |
| 2. Start work from the issue | Creates a branch off `develop` linked to the issue, moves the tracker item to *In progress*, and derives `specs/intent/<slug>.md` from the issue's own description | Ask Claude to use the **`spec-intent`** skill with the issue number |
| 3. Turn the intent into a spec | Generates `specs/features/<slug>.feature` and loops with you on open questions until nothing's left ambiguous | Ask Claude to use the **`spec-feature`** skill, or run `/spec-feature <slug>` |
| 4. Approve the `.feature` file | High-risk changes only: no implementation code gets written before this happens | Human review — no skill runs this step |
| 5. Implementation → acceptance tests → mutation testing → docs | Test-first in speclj, gated by `check-clean.sh`, with a manual playtest (`bb play`) before acceptance tests get wired up | Handled per Steps 4-7 of `.claude/workflow.md` |

**Prerequisites:** the `gh` CLI, installed and authenticated with the
`project` scope (`gh auth login`, or `gh auth refresh -s project` if
already logged in), plus push and project-board access to this repo —
these skills assume a maintainer running them, not an outside
contributor without collaborator access.
