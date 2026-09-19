# Mod format

Everything moddable in Veil is a JSON file under `mods/`. The game core ships
as the mod `core`; it loads through the same path as any third-party mod. This
page is what a mod author writes. How the loader is built (the pure/I-O split,
where the startup decisions live) is in [architecture.md](architecture.md).

The rules below are enforced by specs, not by JSON Schema files: the code is
the schema. The mod machinery (manifests, IDs, ordering, overrides) is
`veil.mods.loader`, `.manifest`, `.ids`, `.order`; the theme content type is
`veil.mods.themes`. A file that breaks a rule fails the launch with every
problem listed (file, JSON path, what was expected) under "Failed to load
mods:". Unknown fields are errors, not ignored.

## Layout

```
mods/
  core/                  one folder per mod; the folder name is the mod's id
    mod.json             the manifest
    themes/
      default.json       one file per content item, in the content type's folder
  goblin-pack/
    mod.json
    themes/...
```

A folder inside a mod that no content type claims, and a file in a claimed
folder that isn't `*.json`, are ignored. Today the only content type is
`themes`.

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
uses. This is how a mod recolors the game: override `core:default`.

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

## Where the mods directory is read from

The game reads `mods/` in the working directory. `-Dveil.mods.dir=<path>`
points it elsewhere (`veil.mods.loader/mods-dir`); the installers set it to the
`mods/` folder they ship next to the jar.
