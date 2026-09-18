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
and mutation testing concentrate. The direction is enforced by
dependency-checker (`dependency-checker.edn`, `bb layers`), and the UML viewer
(`bb uml`) draws any violating edge red.

## Where state lives

- Game state: the fun-mode state map, nowhere else. No atoms in `veil.game`.
- Settings persisted between runs (`settings.json` in the working directory)
  are runtime output and are git-ignored.

## Screens and menu

The game has three screens: `:main-menu`, `:map`, and `:options`. Each screen
consumes a subset of game inputs.

```
:main-menu
  :up/:down    - navigate menu (wrapping)
  :confirm     - select menu item, transition to :map/:options, or set :over?
  :back        - ignored
  
:map
  :back        - return to :main-menu, keeping menu selection
  other inputs - ignored
  
:options
  :back        - return to :main-menu, keeping menu selection
  other inputs - ignored
```

The menu has three items: `"New Game"`, `"Options"`, `"Quit"`. The `:selected`
field (0-based index) tracks which is active, with wrapping at both ends.

## Input translation: pure vs. Quil

`veil.ui.input/event->input` is a pure function that translates Quil key events
(`{:key kw :raw-key char :key-code int}`) to game inputs
(`:up`, `:down`, `:confirm`, `:back`, or `nil`). It has no dependency on Quil,
so specs and acceptance steps can use it directly without opening a window.

`veil.ui.draw` is the only Quil-touching UI namespace. `event->input` is kept
pure (no Quil required) so game input logic can be tested in isolation.

## The Esc/Processing gotcha

Processing calls `exit()` if its `key` field == 27 (ESC char) after `keyPressed`
returns. To make Esc mean `:back` (not quit), `veil.main/key-pressed` wrapper
zeros that field when a raw Escape is detected. This prevents the unintended
exit and allows menu and screen navigation to handle Esc as :back input.
