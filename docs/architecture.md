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
| Entry | `veil.main` | `-main`, launch steps, sketch assembly, the per-frame and per-key callbacks | yes |
| UI | `veil.ui.*` | drawing state as glyphs, mapping key events to game inputs, the QA tooling (`veil.ui.qa.*`) | yes |
| Game | `veil.game.*` | rules: screens, menus, world, entities, the events a state change caused | **no** |

`veil.game` must stay free of Quil and I/O; it is where the specs, CRAP score
and mutation testing concentrate. The direction is enforced by
dependency-checker (`dependency-checker.edn`, `bb layers`), and the UML viewer
(`bb uml`) draws any violating edge red.

<!-- added 2026-09-19: dependency-checker sees only the direction of an arrow, not a rule worked out twice (#25) -->
**`veil.ui` translates; it doesn't decide.** When `veil.game` already answers a
question (is this tile walkable, which menu item is next), `veil.ui` calls it
and translates the result into glyphs or game inputs. Working out the same
answer again from the raw facts is a defect even though every arrow points
down: the rule now exists twice and the copies drift apart. Fix it by calling
`veil.game`, not by moving the duplicate to another namespace. The converse is
also a defect: a `veil.game` function that only specs call while `veil.ui`
reimplements it. Wire `veil.ui` to it, or delete it. No tool detects this; it
is a judgment-checklist line (`docs/clean-code-gate.md`).

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
returns. To make Esc mean `:back` (not quit), `veil.main/handle-key` zeros that
field when a raw Escape is detected. This prevents the unintended
exit and allows menu and screen navigation to handle Esc as :back input.

## QA runs: scripted input through the real key path

`bb qa` plays a key script through the game and checks what happened (usage and
file formats: `docs/testing.md`). It adds no second way for input to reach the
game.

**One input path.** `veil.main/handle-key` is the only place a key event becomes
a game input. A typed key reaches it from Quil's `key-pressed` callback
(`veil.ui.qa.mode/on-key`); a scripted key reaches it from the per-frame
`update` (`mode/frame` -> `driver/advance` -> `play/press`). Both pass the same
`handle-key`, so the Esc/Processing workaround and `input/event->input` apply
to scripted keys exactly as to typed ones.

**Events are derived, not returned.** `veil.game.events/between` diffs two game
states into events (`:menu/selection-changed`, `:screen/changed`,
`:game/over`). `state/handle-input` still takes a state and an input and returns
a state, so no existing caller changed. The cost: an event exists only if the
state shows it. A change with no state difference (pressing Esc on the main
menu) logs the key and no event.

**Placement.** Everything QA-specific lives in `veil.ui.qa.*`, so it belongs to
the `:ui` component: no new component and no edit to `dependency-checker.edn`.
Only `veil.game.events` sits in `veil.game`, since it reads state and nothing
else.

| Namespace | Holds | Pure? |
|---|---|---|
| `veil.ui.qa.script` | key script parsing, script key -> Quil-shaped event | yes |
| `veil.ui.qa.launch` | `--keys` / `--log` parsing, the launch step runner, the child JVM command line | yes |
| `veil.ui.qa.log` | log format v1: header, render, parse | yes |
| `veil.ui.qa.play` | press a key or event through a handler, collect its log entries | yes |
| `veil.ui.qa.driver` | per-frame scripted input (which step is due at this tick) | yes |
| `veil.ui.qa.mode` | the sketch's QA decisions: launch plan, one frame, one live key | yes |
| `veil.ui.qa.procedure` | procedure files, matching a log against expectations, report and summary text | yes |
| `veil.ui.qa.session` | running one procedure or all of them, with every I/O function passed in | yes |
| `veil.ui.qa.files` | read a script, write and append the log | I/O, specced against temp files |
| `veil.ui.qa.runner` | `bb qa` entry point: spawns the game, prints | I/O, not specced |

The shell stays thin the same way `veil.main` does: `session` takes the file,
process and listing functions as arguments (specs pass fakes), so `runner` holds
only the process spawn and the printing, the one part that opens a window.
