# Veil

Veil is a 2D ASCII-style open-world RPG

## Getting the game

Grab the latest installer for your platform from the
[Releases page](https://github.com/SwiftFaze/Veil/releases) —
Windows (`.exe`), Linux (`.deb`), and macOS (`.pkg`, unsigned — right-click
→ Open past the first Gatekeeper warning) are all supported. Prereleases
tagged `-beta.N` are newer, less stable builds; anything without a beta
tag is the current stable release.

## Running from source

Requires JDK 17+ and Maven.

```
mvn compile exec:java
```

## Class/stats sandbox

A dev-only tool that lists every player class and shows its computed
stats (attack power, defense, HP, mana) without needing to actually play
the game. It's not part of the packaged installer.

```
mvn compile exec:java -Dexec.mainClass=com.swiftfaze.veil.sandbox.ClassSandbox
```

Edit a class's JSON under `src/main/resources/classes/` and re-run to see
the change reflected immediately, no recompiling needed.

## Documentation

- [`CLAUDE.md`](CLAUDE.md) — project conventions and guidance for working in this repo with Claude Code
- [`docs/architecture.md`](docs/architecture.md) — entry point, render loop, world model, player classes/stats, UI shell
- [`docs/testing.md`](docs/testing.md) — the three test layers (unit / acceptance / integration) and why they're separated
- [`docs/release.md`](docs/release.md) — versioning, changelog generation, and how releases are built
- [`docs/wiki.md`](docs/wiki.md) — how the player-facing wiki is maintained
- [GitHub Wiki](https://github.com/SwiftFaze/Veil/wiki) — player-facing docs (classes, items, world, etc.)

## Adding a feature or fix (spec-first workflow)

This repo builds every non-trivial change through an intent → spec →
human approval → implementation pipeline, automated with
[Claude Code](https://claude.com/claude-code) skills committed under
`.claude/skills/`. Full policy (model per step, checkpointing,
constraints) lives in `.claude/workflow.md`; the short version:

| Step | What happens | How |
|---|---|---|
| 1. Get the idea into a GitHub issue | Already have one? Skip to step 2. Just want to think out loud and file it for later, not build it now? | Ask Claude to use the **`brainstorm-issue`** skill — it scopes the idea with you and files it (plus a follow-up issue if it splits into "now" vs. "later" work) on the VEIL project board |
| 2. Start work from the issue | Creates a branch off `develop` linked to the issue, moves the tracker item to *In progress*, and derives `specs/intent/<slug>.md` from the issue's own description | Ask Claude to use the **`spec-intent`** skill with the issue number |
| 3. Turn the intent into a spec | Generates `specs/features/<slug>.feature` and loops with you on open questions until nothing's left ambiguous | Ask Claude to use the **`spec-feature`** skill, or run `/spec-feature <slug>` |
| 4. Approve the `.feature` file | No implementation code gets written before this happens | Human review — no skill runs this step |
| 5. Implementation → acceptance tests → mutation testing → docs | Includes a manual playtest (`mvn compile exec:java`) before acceptance tests get wired up | Handled per Steps 4-7 of `.claude/workflow.md` |

**Prerequisites:** the `gh` CLI, installed and authenticated with the
`project` scope (`gh auth login`, or `gh auth refresh -s project` if
already logged in), plus push and project-board access to this repo —
these skills assume a maintainer running them, not an outside
contributor without collaborator access.
