# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-09-19T20:52:43.879140Z","feature_name":"Fonts","feature_path":"specs/features/fonts.feature","background_hash":"179c262436ff6b7987c1736603ab96fc75a3b33c86cd51155b9d9232e271d576","implementation_hash":"unknown","scenarios":[]}
# acceptance-mutation-manifest-end

Feature: Fonts
  A font is mod content: a small JSON descriptor mods/<mod>/fonts/<name>.json
  that names a font file (.ttf or .otf) in the same folder and the pixel size
  to draw it at. Fonts are the second content type registered with the mod
  loader, after themes. The game draws in one font, always core:default; a mod
  changes the font by overriding it. The game ships JetBrains Mono as
  core:default.

  Covers: the "fonts" content type (folder "fonts"), the descriptor's fields
    (id, file, size, and overrides as for any content type), the rules for
    file (a bare .ttf or .otf name, present in the same mod's fonts folder) and
    size (a positive integer), the font the game starts with (core:default),
    replacing it by overriding core:default, the startup failure when
    core:default isn't registered, and the shipped core font (its descriptor,
    its glyph coverage, its license file, and that it is the only font file the
    core mod ships).
  Supersedes: nothing. The cell-grid feature's first draft assumed a font on
    the classpath; that assumption is dropped, not superseded.
  Out of scope: the mod loader machinery itself (mod-loader.feature) - the
    content-ID pattern, collisions, "overrides" rules, dependency ordering and
    the "all problems reported together" rule apply to fonts as to any other
    content type and are not re-specified here. Also out: choosing among
    registered fonts and persisting the choice (a future mod screen); more than
    one weight or style, fallback fonts, and checking that a font is
    monospace; what a font looks like or how the grid uses its size
    (terminal-cell-grid.feature); and a font file that can't be opened as a
    font (Java I/O at launch, covered by a unit spec over a temp file).
  QA: none - what a font looks like doesn't appear in a key script's event log,
    so the human playtest is the check.

  Background:
    Given mod "core" has no dependencies

  # --- Loading a font ---

  Scenario: A font with an id, a file that is there and a size loads and is registered
    Given mod "core" has a font "core:default" using file "Mono.ttf" at size 20
    And mod "core" has the font file "core/fonts/Mono.ttf"
    When the mods are loaded
    Then font "core:default" is registered from mod "core" with file "core/fonts/Mono.ttf" and size 20

  Scenario: The font file may be an OpenType file
    Given mod "core" has a font "core:default" using file "Mono.otf" at size 20
    And mod "core" has the font file "core/fonts/Mono.otf"
    When the mods are loaded
    Then font "core:default" is registered from mod "core" with file "core/fonts/Mono.otf" and size 20

  Scenario: The core mod's shipped font loads
    Given mod "core" has the shipped font "core/fonts/default.json"
    When the mods are loaded
    Then font "core:default" is registered from mod "core" with file "core/fonts/JetBrainsMono-Regular.ttf"

  # --- The descriptor's fields ---

  Scenario Outline: Every field of a font must be present
    Given mod "core" has the font descriptor "core/fonts/default.json" containing <json>
    When the mods are loaded
    Then the load errors include the file "core/fonts/default.json", the path "<path>" and that it expected a required field

    Examples:
      | json                                          | path  |
      | {"id": "core:default", "size": 20}            | /file |
      | {"id": "core:default", "file": "Mono.ttf"}    | /size |
      | {"file": "Mono.ttf", "size": 20}              | /id   |

  Scenario Outline: The size must be a positive integer
    Given mod "core" has a font "core:default" using file "Mono.ttf" at size <size>
    And mod "core" has the font file "core/fonts/Mono.ttf"
    When the mods are loaded
    Then <outcome>

    Examples:
      | size | outcome                                                                                                                  |
      | 1    | font "core:default" is registered from mod "core" with file "core/fonts/Mono.ttf" and size 1                             |
      | 200  | font "core:default" is registered from mod "core" with file "core/fonts/Mono.ttf" and size 200                           |
      | 0    | the load errors include the file "core/fonts/default.json", the path "/size" and that it expected a positive integer     |
      | -5   | the load errors include the file "core/fonts/default.json", the path "/size" and that it expected a positive integer     |
      | 12.5 | the load errors include the file "core/fonts/default.json", the path "/size" and that it expected a positive integer     |
      | "20" | the load errors include the file "core/fonts/default.json", the path "/size" and that it expected a positive integer     |

  Scenario Outline: The file must be a bare .ttf or .otf name
    Given mod "core" has a font "core:default" using file "<file>" at size 20
    When the mods are loaded
    Then the load errors include the file "core/fonts/default.json", the path "/file" and that it expected a font file name ending in .ttf or .otf

    Examples:
      | file           |
      | sub/Mono.ttf   |
      | ../Mono.ttf    |
      | /Mono.ttf      |
      | Mono.woff      |
      | Mono           |
      | Mono.ttf.txt   |

  Scenario: A field that isn't part of a font is rejected
    Given mod "core" has the font descriptor "core/fonts/default.json" containing {"id": "core:default", "file": "Mono.ttf", "size": 20, "family": "Mono"}
    When the mods are loaded
    Then the load errors include the file "core/fonts/default.json", the path "/family" and that it expected no such field

  # --- The font file has to be there ---

  Scenario: A descriptor whose font file isn't in the mod's fonts folder fails the load
    Given mod "core" has a font "core:default" using file "Mono.ttf" at size 20
    When the mods are loaded
    Then the load errors include the file "core/fonts/default.json", the path "/file" and that it expected the font file "Mono.ttf" in "core/fonts"

  Scenario: A font file in another mod's fonts folder doesn't count
    Given mod "core" has a font "core:default" using file "Mono.ttf" at size 20
    And mod "other-pack" has no dependencies
    And mod "other-pack" has the font file "other-pack/fonts/Mono.ttf"
    When the mods are loaded
    Then the load errors include the file "core/fonts/default.json", the path "/file" and that it expected the font file "Mono.ttf" in "core/fonts"

  Scenario: A font file kept in a different folder of the mod doesn't count
    Given mod "core" has a font "core:default" using file "Mono.ttf" at size 20
    And mod "core" has the font file "core/themes/Mono.ttf"
    When the mods are loaded
    Then the load errors include the file "core/fonts/default.json", the path "/file" and that it expected the font file "Mono.ttf" in "core/fonts"

  # --- The font the game uses ---

  Scenario: The game starts with core:default as its font
    Given mod "core" has a font "core:default" using file "Mono.ttf" at size 20
    And mod "core" has the font file "core/fonts/Mono.ttf"
    When the mods are loaded
    And the font for startup is chosen from the loaded mods
    Then the startup font is the file "core/fonts/Mono.ttf" at size 20

  Scenario: A mod that overrides core:default replaces the font
    Given mod "core" has a font "core:default" using file "Mono.ttf" at size 20
    And mod "core" has the font file "core/fonts/Mono.ttf"
    And mod "retype-pack" depends on "core"
    And mod "retype-pack" has a font "core:default" using file "Wide.otf" at size 16 that overrides "core:default"
    And mod "retype-pack" has the font file "retype-pack/fonts/Wide.otf"
    When the mods are loaded
    And the font for startup is chosen from the loaded mods
    Then the startup font is the file "retype-pack/fonts/Wide.otf" at size 16

  Scenario: A font that doesn't override core:default is loaded and not used
    Given mod "core" has a font "core:default" using file "Mono.ttf" at size 20
    And mod "core" has the font file "core/fonts/Mono.ttf"
    And mod "runic-pack" depends on "core"
    And mod "runic-pack" has a font "runic-pack:runic" using file "Runic.ttf" at size 18
    And mod "runic-pack" has the font file "runic-pack/fonts/Runic.ttf"
    When the mods are loaded
    And the font for startup is chosen from the loaded mods
    Then font "runic-pack:runic" is registered from mod "runic-pack" with file "runic-pack/fonts/Runic.ttf" and size 18
    And the startup font is the file "core/fonts/Mono.ttf" at size 20

  Scenario: Starting with no fonts registered at all fails loudly
    Given mod "core" ships no fonts
    When the mods are loaded
    And the font for startup is chosen from the loaded mods
    Then starting fails naming the missing font "core:default"

  Scenario: Starting when fonts are registered but core:default isn't fails loudly
    Given mod "core" has a font "core:other" using file "Mono.ttf" at size 20
    And mod "core" has the font file "core/fonts/Mono.ttf"
    When the mods are loaded
    And the font for startup is chosen from the loaded mods
    Then starting fails naming the missing font "core:default"

  # --- The shipped core font ---

  Scenario: The shipped core font has a glyph for every box-drawing and block-element character
    Then the shipped font file "core/fonts/JetBrainsMono-Regular.ttf" has a glyph for every code point from U+2500 to U+259F

  Scenario: The shipped core font has a glyph for every printable ASCII character
    Then the shipped font file "core/fonts/JetBrainsMono-Regular.ttf" has a glyph for every code point from U+0020 to U+007E

  Scenario: The font's license ships beside it
    Then the shipped file "core/fonts/OFL.txt" exists
    And the shipped file "core/fonts/OFL.txt" contains "SIL OPEN FONT LICENSE"

  Scenario: The core mod ships exactly one font file
    Then the shipped folder "core/fonts" holds exactly 1 font file

# Non-goals:
#   - Re-specifying the loader: the namespaced ID pattern, collisions,
#     "overrides" semantics (including one mod overriding without depending on
#     the owner), dependency ordering and "every validation problem reported
#     together" belong to mod-loader.feature. Fonts get scenarios only where
#     the font spec adds a rule of its own (the three fields, the file name
#     rule, the file being present, the size).
#   - Choosing among registered fonts, in Options or anywhere else, and
#     persisting the choice: a future mod screen, where the player activates
#     mods and their content. "A font that doesn't override core:default is
#     loaded and not used" pins today's behavior, which that screen will change.
#   - Whether a font is monospace, one weight or one style. The core mod's
#     "exactly one font file" is checked; a third-party mod is trusted.
#   - The size the shipped font is drawn at. It is tuned by eye in the playtest
#     (about 20 px gives a 12x25 cell), so no scenario pins core:default's size.
#   - Upper-case extensions (Mono.TTF) and file names with a backslash: the
#     scenarios pin "/" and "..", and the coder decides the rest by the rule
#     "no path separator, ending in .ttf or .otf".
#
# Risks:
#   - A font file that exists and ends in .ttf but isn't a font is not caught
#     by any scenario: telling needs Java (Font/createFont), so it is a launch
#     step with a unit spec over a junk temp file. It stops the launch with a
#     message naming the file and exit status 1 (Clarifications), but the
#     scenarios above can't prove it.
#   - The loader's content-type contract doesn't see other files today, so
#     "the file is in the mod's fonts folder" needs the loader to hand the
#     in-memory file map (or its keys) to the check. A change to that shared
#     contract touches every content type, so raise it before merging rather
#     than absorbing it; mod-loader.feature and themes.feature must still pass
#     unchanged.
#   - veil.mods.disk reads every file one folder below a mod as text, so the
#     roughly quarter-megabyte font is decoded to a string and thrown away on
#     every launch. Harmless but wasteful; reading only its presence is a
#     coder decision, and the "file is there" scenarios hold either way.
#   - The registered file is relative to the mods directory
#     ("core/fonts/Mono.ttf"), and the step "the font for startup is chosen"
#     returns that, not a machine path. veil.main joins it to the mods
#     directory; that join needs a live launch and is covered by the QA run.
#   - Three shipped-file scenarios read the repo's mods/ folder from the working
#     directory, as themes' "ported from Java Veil" step does. They fail if the
#     file is renamed, which is the point; they never touch a path on one
#     machine.
#   - The glyph checks need the font parsed in the acceptance JVM
#     (Font/createFont from the .ttf); it works headless.
#   - The "every printable ASCII" check is broader than what the three screens
#     print today; it is there so the next widget's text can't hit a missing
#     glyph.
#
# Open questions: none. All four were settled in specs/intent/fonts.md's
#   Clarifications.
