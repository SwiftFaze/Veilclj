# Architecture

Veil is a 2D ASCII-tile game in Clojure. Every tile is a character glyph drawn
with [Quil](http://quil.info) (a Clojure wrapper over Processing); there is no
sprite pipeline.

## Functional core, imperative shell

The whole game is **one immutable state map**. Quil's `fun-mode` middleware
threads it through the sketch:

```
setup      -> initial state
update     state -> state              (per frame)
key-pressed state, event -> state      (per key)
draw       state -> side effects only  (never returns new state)
```

Everything that decides *what happens* is a pure function of the state and an
input, so specs exercise it directly with no window. Only drawing and the
sketch wiring touch Quil.

## Layers

Dependencies point one way, downward. Nothing may depend on a layer above it.

| Layer | Namespaces | Holds | May call Quil? |
|---|---|---|---|
| Entry | `veil.main` | `-main`, sketch assembly | yes |
| UI | `veil.ui.*` | drawing state as glyphs, mapping key events to game inputs | yes |
| Game | `veil.game.*` | rules: screens, menus, world, entities | **no** |

`veil.game` must stay free of Quil and I/O; it is where the specs, CRAP score
and mutation testing concentrate. This direction is mechanically enforced by
dependency-checker once it lands (tracked on the board; see `testing.md`).

## Where state lives

- Game state: the fun-mode state map, nowhere else. No atoms in `veil.game`.
- Settings persisted between runs (`settings.json` in the working directory)
  are runtime output and are git-ignored.
