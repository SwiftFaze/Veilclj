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
| Entry | `veil.main` | `-main`, launch steps, sketch assembly, the per-frame and per-key callbacks: Quil calls and passing data along, no decisions (`bb shell-check`) | yes |
| UI | `veil.ui.*` | drawing state as a grid of character cells ("The cell grid" below), mapping key events to game inputs, the QA tooling (`veil.ui.qa.*`) | yes |
| Game | `veil.game.*` | rules: screens, menus, world, entities, the events a state change caused, the color lookup (`veil.game.theme`) | **no** |
| Mods | `veil.mods.*` | reading `mods/` and building the registry, content types and their validation (themes: `veil.mods.themes`, fonts: `veil.mods.fonts`); file format: `mod-format.md` | no; only `veil.mods.disk` does I/O |

`veil.game` must stay free of Quil and I/O; it is where the specs, CRAP score
and mutation testing concentrate. The direction is enforced by
dependency-checker (`dependency-checker.edn`, `bb layers`), and the UML viewer
(`bb uml`) draws any violating edge red.

<!-- added 2026-09-19: dependency-checker sees only the direction of an arrow, not a rule worked out twice -->
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
  Besides the screen, menu and player it holds the mods registry (`:mods`), the
  loaded themes (`:themes`, theme id -> its 19 colors) and the id of the active
  one (`:active-theme`). The font is not in the state: only the sketch's setup
  needs it, so `veil.main` opens it once at launch and passes its path and size
  to `setup`.
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

`veil.ui.input/escape?` answers "is this the raw Escape key?", so the shell only
has to act on the answer (next section). `veil.ui.draw` is the only
Quil-touching UI namespace. `event->input` and `escape?` are kept pure (no Quil
required) so game input logic can be tested in isolation.

## The cell grid: state to buffer to draw commands to pixels

The game draws like a terminal: a grid of character cells, as many whole cells
as fit the window and never fewer than 80 columns by 24 rows. Rendering is a
pure pipeline over data, and only its last step touches Quil:

```
state --view/buffer--> buffer --commands/frame--> draw commands --draw!--> pixels
        (which text,             (pixel position,      (rect / text calls,
         which cells)             RGB colors)           no decisions)
```

| Namespace | Holds |
|---|---|
| `veil.ui.buffer` | the buffer, `{:cols :rows :cells}`: a vector of rows of cells, each `{:glyph :fg :bg}`. `blank`, `cell`, `row-text`, `write-text`, `fill-rect` and `draw-box` return new buffers; anything outside the grid is clipped |
| `veil.ui.grid` | `size`: whole cells that fit a window in pixels, with the 80x24 minimum; `cell-size`: a cell's pixel size from a character's measured width and the font's ascent and descent |
| `veil.ui.commands` | `frame`: a buffer to `{:background :rects :glyphs}`, the pixel-positioned commands |
| `veil.ui.view` | what each screen shows: `buffer` writes a screen's lines into a buffer (centered across, at their rows, reverse video on the selected menu item, a single-line border around the whole grid); `scene` composes cell size, grid size, buffer and commands |
| `veil.ui.font` | opens the font file (I/O; see below) |
| `veil.ui.draw` | carries the commands out with Quil; no decisions |

**Cells hold theme keys, not colors.** A cell's `:fg` and `:bg` are keys such
as `:BORDER`. `commands/frame` resolves them to RGB through `veil.game.theme/color`
when it builds the commands, so a recolored theme changes the next frame and
never the buffer, and a key the theme lacks throws there, loudly. A cell whose
background is the frame's `:BACKGROUND` adds no rectangle and a space adds no
glyph, so a blank buffer draws only the background.

**Cell size comes from the font, measured by the shell.** `draw!` reads the
window's pixel size, the width of one "M" and the font's ascent and descent
from Quil and hands them to `view/scene`; every calculation on them
(`grid/cell-size`, `grid/size`, pixel positions) is a pure function. The
window is resizable, and each frame recomputes the grid from the current
window, so the grid follows a resize with no resize handler. The font is
`core:default`, a mod's content (`mod-format.md`, "Fonts"), loaded once at
launch. `veil.ui.font/open` is the one place a font file is read: it returns
`{:font-path :size}` for `q/create-font`, or an error naming the file. It is
I/O, so it is specced against real and temp files and is not a mutation target.

**Single answer.** `view/buffer` asks `veil.game.state` (`screen`,
`menu-items`, `selected-item`) what to show and never works out the selection
itself; the colors are `veil.game.theme/color`'s.

## The Quil shell decides nothing

`veil.main` and `veil.ui.draw` call Quil and Processing and pass data along;
every decision and calculation is a specced pure function, so the only lines no
spec runs are the ones that need a live window. The rule and the mechanical
check (`bb shell-check`, a blocking section of the gate) are in
`docs/testing.md`, "The Quil shell rule". Where each decision lives:

| Decision the shell used to make | Now answered by | Layer |
|---|---|---|
| Did the mods load, and what does a failure say and exit with? | `veil.mods.loader/startup` (registry, or error report plus exit status 1) | mods |
| Which themes does the game have, and does the launch stop without the default one? | `veil.mods.themes/startup` (`{:themes ..}`, or error report plus exit status 1 when `core:default` isn't registered) | mods |
| Which mods directory is read? | `veil.mods.loader/mods-dir` (the `veil.mods.dir` property, else `mods`) | mods |
| What state does the game start in? | `veil.game.state/starting` (registry and themes) | game |
| Which color is drawn? | `veil.game.theme/color` (a key looked up in the active theme in the state); `veil.ui.view` only asks, `veil.mods.themes/construct` resolved every optional key's fallback at load | game |
| Does the launch start the game or stop it with a message? | `veil.ui.qa.launch/outcome` | ui |
| Is this key the raw Escape that Processing would treat as quit? | `veil.ui.input/escape?` | ui |
| Which font does the game draw in, and does the launch stop without it? | `veil.mods.fonts/startup` (`{:font ..}`, or error report plus exit status 1 when `core:default` isn't registered) | mods |
| Can the font file be opened, and at what size? | `veil.ui.font/open` (`{:font-path :size}`, or an error naming the file, which stops the launch with exit status 1) | ui |
| How many cells fit the window, and how big is one? | `veil.ui.grid/size` and `grid/cell-size`; `draw!` only passes `(q/width)`, `(q/height)` and the text metrics in | ui |
| What does each screen show, and where? | `veil.ui.view/buffer` (state, columns, rows -> a buffer) | ui |
| Where and in what colour is each cell drawn? | `veil.ui.commands/frame` (buffer, cell size -> `:rects` and `:glyphs` with `:x`, `:y`, `:w`, `:h` and RGB `:color`); `veil.ui.view/scene` composes the pipeline | ui |
| Is it time to quit? | `veil.ui.qa.mode/frame`'s `:exit?` | ui |

The startup decision is split at its seams so that no layer gained a
dependency: `loader/startup` needs only `veil.mods.loader`, `themes/startup`
only `veil.mods.themes`, `fonts/startup` only `veil.mods.fonts`, `starting`
only `veil.game.state`. `veil.main` runs the launch as a list of steps, each
taking what the earlier ones produced and stopping the launch at the first
`{:error ..}`: plan the launch arguments; read the mods directory
(`veil.mods.disk`, at the path `loader/mods-dir` chose) and call
`loader/startup` with the content types (`themes/content-type`,
`fonts/content-type`); `themes/startup` on the registry it got back;
`fonts/startup` on the same registry; `veil.ui.font/open` on the font it chose
(relative to the mods directory); start the log. It hands the registry and
`themes/all`'s theme map to `starting`, and the opened font's path and size to
`q/create-font`, when the sketch sets up. `veil.game.theme` knows nothing of
`veil.mods.*`: the theme map reaches it only as data in the state.
`dependency-checker.edn` did not change. `draw!` passes the window's pixel size
and text metrics to `view/scene` rather than the view knowing the window, so
the resizable window lays out correctly on every frame.

**Content types with a check.** A content type is `{:type :folder :spec
:phrases :construct}` and, optionally, `:check`. Themes need only their spec;
a font also has to name a file that exists, which a spec over one JSON file
cannot see. `veil.mods.loader` therefore calls a type's `:check` with
`{:data :mod :file :paths}` (`:paths` is every file path the mods data holds,
so the check stays pure) once the file satisfies its spec, and reports the
error maps it returns with the other load problems, in
`veil.mods.validate/field-error`'s shape.

## The Esc/Processing gotcha

Processing calls `exit()` if its `key` field == 27 (ESC char) after `keyPressed`
returns. To make Esc mean `:back` (not quit), `veil.main/handle-key` calls
`prevent-processing-exit`, which zeros that field when `input/escape?` says the
event is a raw Escape. This prevents the unintended exit and allows menu and
screen navigation to handle Esc as :back input. The `set!` on the applet needs a
live window, so it is the one part of this that only the QA run and the
playtest exercise.

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
