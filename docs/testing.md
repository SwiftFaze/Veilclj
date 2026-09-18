# Testing

## Unit specs (speclj)

Specs live in `spec/`, mirroring `src/`: `src/veil/game/menu.clj` is specified
by `spec/veil/game/menu_spec.clj` (namespace `veil.game.menu-spec`).

```
bb spec                      # all specs
bb spec -f d                 # documentation format
bb spec -a                   # autotest: rerun on every save
```

Rules of thumb:

- Spec the pure `veil.game` layer directly - no window, no Quil.
- One behavior per `it`, with a description naming that behavior.
- Arrange / act / assert, in that order, visibly.

## Coverage (Cloverage)

```
bb cov                       # -> target/coverage/index.html and lcov.info
```

## Tooling roadmap

The rest of Uncle Bob's toolchain lands in the "Uncle Bob tooling" milestone
and each tool gets documented here when it does:

- speclj-structure-check - catches mis-nested `describe`/`it` forms
- crap4clj - CRAP score per function, gating complexity x missing coverage
- clj-mutate - mutation testing of changed namespaces
- dependency-checker - enforces the layer direction in `architecture.md`
- Acceptance pipeline - `features/*.feature` -> JSON IR -> generated speclj
- uml-viewer - live class diagram of the codebase
