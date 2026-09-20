# Keyboard input

How a key press becomes something `veil.game` can act on, and who gets offered
it first. Layering and the rest of the pipeline: [architecture.md](architecture.md).

## Translation: pure vs. Quil

`veil.ui.input/event->input` is a pure function from a Quil key event
(`{:key kw :raw-key char :key-code int :modifiers #{...}}`) to a game input,
one of:

- a **navigation action**, a bare keyword — `:up :down :left :right :confirm
  :back :tab :shift-tab :toggle :backspace :delete :home :end :page-up
  :page-down`
- a **printable character**, `{:char \w}`
- a **character with modifiers held**, `{:char \a :mods #{:ctrl}}`
- `nil`, for a key with no translation

It knows nothing about WASD or any other screen-specific alias: those live
where the screen binds inputs (`veil.game.state`), so a focused text field
receives a raw `w` rather than "move up". It requires no Quil, so specs and
acceptance steps call it directly without opening a window.

<!-- added 2026-09-20: a translator that read :key silently dropped six keys and hijacked four printable characters -->
**`:raw-key` says whether a key is coded, not `:key`.** A key Processing can't
express as a character — arrows, Home, End, Page Up, Page Down, F-keys, Caps
Lock — arrives with `:raw-key` = `(char 65535)` (AWT's `CHAR_UNDEFINED`) and
its real value in `:key-code`. Every other key, including Tab, Enter, Esc,
Space, Backspace and Delete, arrives as its actual character in `:raw-key`.
`event->input` branches on that sentinel and never reads `:key`: Quil derives
`:key` from the same pair (`quil.core/key-as-keyword`), and a coded key with no
entry in Quil's `KEY-CODES` table — Home, End, Page Up, Page Down, Caps Lock;
only arrows and F-keys are in it — becomes `:unknown-key` whichever key it was.
Reading a coded key's number out of `:raw-key` is how chars 33-36 (`!"#$`) once
translated to `:page-up :page-down :end :home`.

Modifiers are not in Quil's event map. `veil.main` reads `(q/key-modifiers)`
and merges it in as `:modifiers` before translating; that read needs a live
window, so only the playtest and `bb qa` exercise it.

## The focus-first dispatch chain

`veil.game.dispatch/dispatch` walks a caller-supplied sequence of handlers —
`(fn [state input] -> state-or-nil)` — offering each the input in turn. `nil`
means "not consumed, keep going"; a returned state means consumed, and the walk
stops there. It returns `[state consumed?]`, so the caller owns the
fall-through rather than the chain owning it.

The chain is an **argument**, not a field in the game state: nothing has focus
recorded yet, because nothing plugs into the chain until overlays, panes and
widgets exist.

`veil.game.state/handle-input-with-chain` is that caller — chain first, then
the screen-level bindings (`handle-main-menu`, `handle-map`, `handle-options`)
only if nothing consumed the input. `handle-input` is the same thing with an
empty chain, so every existing caller sees no change. `veil.game.dispatch`
knows nothing of screens, Quil or I/O; it walks the list it is given.

## The Esc/Processing gotcha

Processing calls `exit()` if its `key` field == 27 (ESC char) after `keyPressed`
returns. To make Esc mean `:back` (not quit), `veil.main/handle-key` calls
`prevent-processing-exit`, which zeros that field when `input/escape?` says the
event is a raw Escape. The `set!` on the applet needs a live window, so it is
the one part of this that only the QA run and the playtest exercise.
