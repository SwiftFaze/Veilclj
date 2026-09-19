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
bb acceptance  # Gherkin acceptance tests
bb uber        # runnable jar -> target/veil-<version>.jar
bb tasks       # list every task, including the quality tools
```

The quality tooling is Uncle Bob's: speclj, SCRAP, crap4clj, clj-mutate,
dependency-checker, dry4clj, the Acceptance Pipeline Specification,
deintroverter and uml-viewer - see [`docs/testing.md`](docs/testing.md).

Themes and other content are read from the `mods/` folder in the working
directory (`-Dveil.mods.dir=<path>` points elsewhere), so running the jar
directly needs it: `java -jar target/veil-*.jar` from the repo root works; from
anywhere else the game exits at launch with "Failed to load mods:". The
installers ship `mods/` and set the property themselves. File format:
[`docs/mod-format.md`](docs/mod-format.md).

On Windows, `scoop install babashka` (from the `scoop-clojure` bucket) is the
easiest route.

## Project layout

```
src/veil/        game source: main (entry) -> ui (Quil drawing) -> game (pure rules); mods (mod loading)
mods/            shipped content: the core mod (themes)
spec/veil/       speclj unit specs, mirroring src/
specs/features/  Gherkin acceptance specs
acceptance/      acceptance pipeline: generator, runtime, step handlers
tools/           bb-side tooling (APS fetcher, CRAP gate, gate ratchet)
specs/intent/    intent-doc template (the docs themselves are local scratch)
docs/            narrative documentation, indexed by docs/README.md
```

## Documentation

- [`CLAUDE.md`](CLAUDE.md) — project conventions and guidance for working in this repo with Claude Code
- [`docs/architecture.md`](docs/architecture.md) — functional core / imperative shell, and the layer rules
- [`docs/mod-format.md`](docs/mod-format.md) — the `mods/` folder: manifests, IDs, load order, overrides and theme files
- [`docs/testing.md`](docs/testing.md) — every test layer and quality tool, and when each one gates
- [`docs/clean-code-gate.md`](docs/clean-code-gate.md) — the `check-clean.sh` gate every change passes
- [`docs/release.md`](docs/release.md) — versioning, changelog generation, and how releases are built
