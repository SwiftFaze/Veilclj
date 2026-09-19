# Mod format

Everything moddable in Veil is a JSON file under `mods/`. The game core ships
as the mod `core`; it loads through the same path as any third-party mod. This
page is what a mod author writes. How the loader is built (the pure/I-O split,
where the startup decisions live) is in [architecture.md](architecture.md).

The rules below are enforced by specs, not by JSON Schema files: the code is
the schema. The mod machinery (manifests, IDs, ordering, overrides) is
`veil.mods.loader`, `.manifest`, `.ids`, `.order`; the theme content type is
`veil.mods.themes` and the font content type is `veil.mods.fonts`. A file that breaks a rule fails the launch with every
problem listed (file, JSON path, what was expected) under "Failed to load
mods:". Unknown fields are errors, not ignored.

## Layout

```
mods/
  core/                  one folder per mod; the folder name is the mod's id
    mod.json             the manifest
    themes/
      default.json       one file per content item, in the content type's folder
    fonts/
      default.json       a font's descriptor ...
      JetBrainsMono-Regular.ttf   ... and the font file it names, beside it
  goblin-pack/
    mod.json
    themes/...
```

A folder inside a mod that no content type claims, and a file in a claimed
folder that isn't `*.json`, are ignored (a font file is data a descriptor
names, not content of its own). Today the content types are `themes` and
`fonts`.

## mod.json

```json
{
  "id": "goblin-pack",
  "dependsOn": ["core"]
}
```

- `id` (required): must equal the folder name.
- `dependsOn` (optional): a list of mod ids that must be present. A missing mod,
  or a cycle, fails the load.

## IDs

Every content item has an `"id"` of the form `<mod>:<name>`, e.g.
`core:default`. Both halves are lowercase, start with a letter or digit, and
then use only letters, digits, `_` and `-`
(pattern: `veil.mods.ids/id-pattern`). A mod may only add ids in its own
namespace: `goblin-pack` may declare `goblin-pack:night`, not `core:night`.

## Load order and overrides

**Load order.** `core` loads first; then any mod whose dependencies have all
loaded, alphabetically by id among the mods ready at the same time. Mods that
don't need `core` may omit it. Content is registered in that order.

**Overrides.** Two files declaring the same id is an error (a collision), so
one mod replaces another's content by adding `"overrides": "<the id>"` to the
file, where the value must equal the file's own `"id"`. The mod must depend,
directly or transitively, on the mod that owns the id, and the id must already
exist. Because the overriding mod loads later, its version is the one the game
uses. This is how a mod recolors the game: override `core:default` in
`themes`; to change its font, override `core:default` in `fonts`. The two are
separate content types, so the same id in each does not collide.

## Themes (`mods/<mod>/themes/<name>.json`)

A theme is a named set of UI colors. One theme is active at a time (always
`core:default` for now; picking one is a later feature) and every color the
game draws is looked up in it by key. The game refuses to start unless
`core:default` is registered.

```json
{
  "id": "core:default",
  "colors": {
    "SELECTED_HIGHLIGHT": { "r": 192, "g": 192, "b": 192 },
    "NORMAL_TEXT":        { "r": 255, "g": 255, "b": 255 }
  }
}
```

(abbreviated: `colors` must contain all 13 required keys below; the shipped
`mods/core/themes/default.json` is a complete example, byte-identical to Java
Veil's.)

- `id` (required) and `overrides` (optional): as above.
- `colors` (required): an object of color keys, each a color object
  `{"r": 0-255, "g": 0-255, "b": 0-255}`. Each channel is an integer from 0 to
  255; all three are required, and no other field is allowed.

**Required keys (13):** `SELECTED_HIGHLIGHT`, `SELECTED_TEXT`, `NORMAL_TEXT`,
`DIMMED_TEXT`, `BACKGROUND`, `INVALID_HIGHLIGHT`, `VALID_HIGHLIGHT`,
`TABLE_HEADER_BACKGROUND`, `BORDER`, `SCROLLBAR_THUMB`, `ACCENT`,
`WINDOW_BORDER`, `TABLE_HEADER_TEXT`.

**Optional keys (6),** each taking the color of a required key when the theme
leaves it out:

| Optional key | Falls back to |
|---|---|
| `SUCCESS` | `VALID_HIGHLIGHT` |
| `ERROR` | `INVALID_HIGHLIGHT` |
| `WARNING` | `ACCENT` |
| `INFO` | `ACCENT` |
| `FOCUSED_BORDER` | `ACCENT` |
| `SHADOW` | `BACKGROUND` |

Any other key is rejected. The fallbacks are resolved once, at load
(`veil.mods.themes/construct`), so the registered theme always has all 19 keys
as `[r g b]`, and the game never has to ask which key to fall back to.

## Fonts (`mods/<mod>/fonts/<name>.json`)

A font is a font file plus the pixel size to draw it at. The game draws
everything in one font, always `core:default` for now (picking one is a later
feature). It refuses to start unless `core:default` is registered, and refuses
to start if that font's file can't be opened as a font, naming the file.

The descriptor and the font file it names sit side by side in the mod's
`fonts/` folder:

```
mods/core/fonts/
  default.json                 the descriptor
  JetBrainsMono-Regular.ttf    the font file
  OFL.txt                      its license
```

```json
{
  "id": "core:default",
  "file": "JetBrainsMono-Regular.ttf",
  "size": 20
}
```

- `id` (required) and `overrides` (optional): as above.
- `file` (required): a bare file name ending in `.ttf` or `.otf`, with no slash or
  backslash in it. The file must exist in the **same mod's** `fonts/` folder
  (`mods/<mod>/fonts/<file>`); a file of that name in another mod's `fonts/`
  folder, or in another folder of the same mod, doesn't count. This is checked
  at load (`veil.mods.fonts/check`), so a missing file is reported with the
  other load problems, as `expected the font file "<file>" in "<mod>/fonts"`.
- `size` (required): a positive integer, the size in pixels the font is drawn
  at. The game measures one character at that size to get the width and
  height of a grid cell (`veil.ui.grid/cell-size`), so `size` sets how many
  columns and rows fit the window; it is tuned by eye, not derived.

No other field is allowed. The game does not check that a font is monospace:
the grid assumes it, so a proportional font draws with uneven spacing.

**Replacing the font.** A mod that depends on `core` adds a font file and a
descriptor with `"id": "core:default"` and `"overrides": "core:default"`
(same rules as any override), and its version is the one the game draws in.
A font with any other id is loaded and registered but not used yet.

**The shipped font** is JetBrains Mono Regular at size 20 (about a 12 by 25 pixel
cell), chosen because it covers printable ASCII and the box-drawing and block
element characters (U+2500 to U+259F) the grid draws with. It is the only font
file `mods/core/fonts/` ships, under the SIL Open Font License 1.1, whose text
(`OFL.txt`) ships beside it: keep it there when redistributing.

## Where the mods directory is read from

The game reads `mods/` in the working directory. `-Dveil.mods.dir=<path>`
points it elsewhere (`veil.mods.loader/mods-dir`); the installers set it to the
`mods/` folder they ship next to the jar.
