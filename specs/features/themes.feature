# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-09-19T18:32:12.267323400Z","feature_name":"Themes","feature_path":"specs/features/themes.feature","background_hash":"179c262436ff6b7987c1736603ab96fc75a3b33c86cd51155b9d9232e271d576","implementation_hash":"unknown","scenarios":[{"index":2,"name":"Every one of the 13 required keys must be present","scenario_hash":"3201b27f31669db222923354322f107b9e259ecf3bc4ad97bc14a1c0d2b39cb0","mutation_count":13,"result":{"Total":13,"Killed":13,"Survived":0,"Errors":0},"tested_at":"2026-09-19T18:31:31.187434Z"},{"index":7,"name":"An optional key a theme omits resolves to its fallback key","scenario_hash":"a3f5cf1aaedd5804b8afc78c29ef6be1954e55453ac8f1859ddc2a605cd8a90c","mutation_count":12,"result":{"Total":12,"Killed":12,"Survived":0,"Errors":0},"tested_at":"2026-09-19T18:31:31.187434Z"},{"index":8,"name":"An optional key a theme defines is used as given","scenario_hash":"03d8b957c0b35e975d4203e36f682fb8698bdf3a4d14a362f25e42c2ed3b2485","mutation_count":12,"result":{"Total":12,"Killed":12,"Survived":0,"Errors":0},"tested_at":"2026-09-19T18:31:31.187434Z"}]}
# acceptance-mutation-manifest-end

Feature: Themes
  A theme is a named set of UI colors shipped by a mod as
  mods/<id>/themes/<name>.json. It is the first real content type registered
  with the mod loader. One theme is active at a time; every color the game
  draws is looked up in it by key, and a key the active theme doesn't define
  resolves to the color its fallback key names.

  Covers: the "themes" content type (folder "themes"), Java Veil's 13 required
    color keys kept verbatim, the {r,g,b} color shape with each channel an
    integer from 0 to 255, the six optional keys and the required key each one
    falls back to, the pure color lookup, the active theme defaulting to
    "core:default", the startup failure when the active theme isn't
    registered, and the main menu and background drawing in the active theme's
    colors.
  Supersedes: the hard-coded colors in veil.ui.view/command-color (selected
    255,255,100 / normal 220,220,220) and the literal black background in
    veil.ui.draw. Both are replaced by theme lookups, so the menu's colors
    change: see "The main menu draws in the active theme's colors". The menu
    is now drawn through the cell grid (terminal-cell-grid.feature), so the
    selected item shows as reverse video: SELECTED_TEXT on SELECTED_HIGHLIGHT,
    where it was once text in SELECTED_HIGHLIGHT.
  Out of scope: the mod loader machinery itself (mod-loader.feature) - the
    content-ID pattern, collisions, "overrides", dependency ordering and the
    "all problems reported together" rule apply to themes exactly as to any
    other content type and are not re-specified here. Also out: choosing a
    theme in Options, persisting the choice, and the core:amber/core:green
    themes (#17); how the cell grid is laid out (terminal-cell-grid.feature,
    which the two "what the player sees" scenarios observe through); and the
    widgets that will use the optional keys (#12, #15).
  QA: none - colors don't appear in a key script's event log, so the human
    playtest is the check for what the menu looks like.

  Background:
    Given mod "core" has no dependencies

  # --- Loading a theme ---

  Scenario: A theme with all 13 required keys loads and is registered
    Given mod "core" has a theme "core:default" with every required color
    When the mods are loaded
    Then theme "core:default" is registered from mod "core"

  Scenario: Java Veil's own default theme file loads unchanged
    Given mod "core" has the theme file "core/themes/default.json" ported from Java Veil
    When the mods are loaded
    Then theme "core:default" is registered from mod "core"
    And theme "core:default" has SELECTED_HIGHLIGHT 192,192,192
    And theme "core:default" has NORMAL_TEXT 255,255,255
    And theme "core:default" has BACKGROUND 0,0,0
    And theme "core:default" has ACCENT 238,179,146
    And theme "core:default" has TABLE_HEADER_TEXT 0,194,194

  Scenario Outline: Every one of the 13 required keys must be present
    Given mod "core" has a theme "core:default" with every required color except "<key>"
    When the mods are loaded
    Then the load errors include the file "core/themes/default.json", the path "/colors/<key>" and that it expected a required field

    Examples:
      | key                     |
      | SELECTED_HIGHLIGHT      |
      | SELECTED_TEXT           |
      | NORMAL_TEXT             |
      | DIMMED_TEXT             |
      | BACKGROUND              |
      | INVALID_HIGHLIGHT       |
      | VALID_HIGHLIGHT         |
      | TABLE_HEADER_BACKGROUND |
      | BORDER                  |
      | SCROLLBAR_THUMB         |
      | ACCENT                  |
      | WINDOW_BORDER           |
      | TABLE_HEADER_TEXT       |

  Scenario: A theme with no colors at all fails naming the colors field
    Given mod "core" has the theme file "core/themes/default.json" containing {"id": "core:default"}
    When the mods are loaded
    Then the load errors include the file "core/themes/default.json", the path "/colors" and that it expected a required field

  Scenario Outline: Each color channel must be an integer from 0 to 255
    Given mod "core" has a theme "core:default" with every required color
    And its BORDER red channel is <channel>
    When the mods are loaded
    Then <outcome>

    Examples:
      | channel | outcome                                                                                                        |
      | 0       | theme "core:default" is registered from mod "core"                                                             |
      | 255     | theme "core:default" is registered from mod "core"                                                             |
      | -1      | the load errors include the file "core/themes/default.json", the path "/colors/BORDER/r" and that it expected an integer from 0 to 255 |
      | 256     | the load errors include the file "core/themes/default.json", the path "/colors/BORDER/r" and that it expected an integer from 0 to 255 |
      | 12.5    | the load errors include the file "core/themes/default.json", the path "/colors/BORDER/r" and that it expected an integer from 0 to 255 |
      | "ff"    | the load errors include the file "core/themes/default.json", the path "/colors/BORDER/r" and that it expected an integer from 0 to 255 |

  Scenario Outline: A color must have all three channels
    Given mod "core" has a theme "core:default" with every required color
    And its BORDER color is <json>
    When the mods are loaded
    Then the load errors include the file "core/themes/default.json", the path "<path>" and that it expected <expected>

    Examples:
      | json                         | path             | expected                   |
      | {"r": 1, "g": 2}             | /colors/BORDER/b | a required field           |
      | {"r": 1, "g": 2, "b": 3, "a": 4} | /colors/BORDER/a | no such field          |
      | [1, 2, 3]                    | /colors/BORDER   | an object with r, g and b  |

  Scenario: A color key that isn't required or optional is rejected
    Given mod "core" has a theme "core:default" with every required color
    And it also defines the color "HYPERLINK"
    When the mods are loaded
    Then the load errors include the file "core/themes/default.json", the path "/colors/HYPERLINK" and that it expected no such field

  # --- The optional keys and their fallbacks ---

  Scenario Outline: An optional key a theme omits resolves to its fallback key
    Given mod "core" has a theme "core:default" with every required color
    And its <fallback> is 7,8,9
    When the mods are loaded
    Then theme "core:default" has <key> 7,8,9

    Examples:
      | key            | fallback          |
      | SUCCESS        | VALID_HIGHLIGHT   |
      | ERROR          | INVALID_HIGHLIGHT |
      | WARNING        | ACCENT            |
      | INFO           | ACCENT            |
      | FOCUSED_BORDER | ACCENT            |
      | SHADOW         | BACKGROUND        |

  Scenario Outline: An optional key a theme defines is used as given
    Given mod "core" has a theme "core:default" with every required color
    And its <fallback> is 7,8,9
    And it also defines <key> as 1,2,3
    When the mods are loaded
    Then theme "core:default" has <key> 1,2,3
    And theme "core:default" has <fallback> 7,8,9

    Examples:
      | key            | fallback          |
      | SUCCESS        | VALID_HIGHLIGHT   |
      | ERROR          | INVALID_HIGHLIGHT |
      | WARNING        | ACCENT            |
      | INFO           | ACCENT            |
      | FOCUSED_BORDER | ACCENT            |
      | SHADOW         | BACKGROUND        |

  # --- The active theme ---

  Scenario: The game starts with core:default active
    Given mod "core" has the theme file "core/themes/default.json" ported from Java Veil
    When the mods are loaded
    And the starting state is built from the loaded mods
    Then the active theme is "core:default"
    And the color for NORMAL_TEXT is 255,255,255

  Scenario: Colors come from whichever theme is active
    Given mod "core" has the theme file "core/themes/default.json" ported from Java Veil
    And mod "amber-pack" depends on "core"
    And mod "amber-pack" has a theme "amber-pack:amber" with every required color
    And its NORMAL_TEXT is 255,176,0
    When the mods are loaded
    And the starting state is built from the loaded mods
    And theme "amber-pack:amber" is made active
    Then the active theme is "amber-pack:amber"
    And the color for NORMAL_TEXT is 255,176,0

  Scenario: A mod that overrides core:default recolors the game
    Given mod "core" has the theme file "core/themes/default.json" ported from Java Veil
    And mod "retheme-pack" depends on "core"
    And mod "retheme-pack" has a theme "core:default" with every required color that overrides "core:default"
    And its NORMAL_TEXT is 10,20,30
    When the mods are loaded
    And the starting state is built from the loaded mods
    Then the active theme is "core:default"
    And the color for NORMAL_TEXT is 10,20,30

  Scenario: Starting with no themes registered at all fails loudly
    Given mod "core" ships no themes
    When the mods are loaded
    And the starting state is built from the loaded mods
    Then starting fails naming the missing theme "core:default"

  Scenario: Starting when themes are registered but core:default isn't fails loudly
    Given mod "core" has a theme "core:other" with every required color
    When the mods are loaded
    And the starting state is built from the loaded mods
    Then starting fails naming the missing theme "core:default"

  # --- What the player sees ---

  Scenario: The main menu draws in the active theme's colors
    Given mod "core" has the theme file "core/themes/default.json" ported from Java Veil
    When the mods are loaded
    And the starting state is built from the loaded mods
    And the screen is rendered into a grid of 80 columns by 24 rows
    And the buffer is turned into draw commands for cells 12 by 25 pixels
    Then the frame's background color is 0,0,0
    And the rectangle command behind "New Game" has the color 192,192,192
    And the glyph commands for "New Game" have the color 0,0,0
    And the glyph commands for "Options" have the color 255,255,255
    And the glyph commands for "Quit" have the color 255,255,255

  Scenario: Recoloring the theme recolors the menu
    Given mod "core" has a theme "core:default" with every required color
    And its SELECTED_HIGHLIGHT is 255,176,0
    And its SELECTED_TEXT is 20,30,40
    And its NORMAL_TEXT is 120,60,0
    And its BACKGROUND is 5,5,5
    When the mods are loaded
    And the starting state is built from the loaded mods
    And the screen is rendered into a grid of 80 columns by 24 rows
    And the buffer is turned into draw commands for cells 12 by 25 pixels
    Then the frame's background color is 5,5,5
    And the rectangle command behind "New Game" has the color 255,176,0
    And the glyph commands for "New Game" have the color 20,30,40
    And the glyph commands for "Options" have the color 120,60,0

# Non-goals:
#   - Re-specifying the loader: the namespaced ID pattern, collisions,
#     "overrides" semantics, dependency ordering, unknown-field rejection in
#     general and "every validation problem reported together" belong to
#     mod-loader.feature. Themes get scenarios only where the theme spec adds
#     a rule of its own (the 13 required keys, the 0-255 channel range, the
#     optional keys, the colors object's shape).
#   - Theme-picking UI, persistence and the amber/green themes: #17. The
#     "made active" step here exercises the state transition only; nothing in
#     the game calls it yet.
#   - Asking for a color key that is neither required nor optional. The key
#     set is closed and written by us, so that is a programmer error, not
#     modder input: the lookup throws an ex-info naming the key, covered by a
#     unit spec, with no Gherkin scenario (Clarifications, Q2).
#   - A missing mods/ directory. It yields an empty registry (mod-loader.feature),
#     which is the same input as "core ships no themes"; the Background's core
#     mod is why the scenario says the latter.
#   - Fallback chains. Every optional key falls back to a *required* key, so
#     resolution is always one hop and can never loop.
#   - The DIMMED_TEXT, BORDER, SCROLLBAR_THUMB, TABLE_HEADER_* and
#     WINDOW_BORDER keys have no drawing scenario: nothing draws a border,
#     scrollbar or table yet. They are required so Java themes stay valid and
#     so #11-#16's widgets find them.
#   - Shipping mods/ with the jpackage installers and the -Dveil.mods.dir
#     launcher property (Clarifications, Q4). In scope for the issue, but it is
#     packaging: no scenario can observe it through the pure steps. A unit spec
#     covers the property-to-directory function; a manual workflow_dispatch run
#     of build-installers covers the rest. No second .feature file.
#
# Risks:
#   - No layer edge is added (Clarifications, Q1). Resolving the optional-key
#     fallbacks is the themes :construct's job, so a registry entry is already
#     a full color map; a veil.mods function projects {id -> colors} for
#     veil.main to hand to state/starting, and veil.game.theme/color reads only
#     the state. dependency-checker.edn must not change; if the coder finds it
#     must, that is a spec deviation to raise, not a detail to absorb.
#   - The resolved colors sit in the state as well as the registry. That is
#     duplicated data, not duplicated logic, but "theme X has KEY r,g,b" must
#     be asserted against the state's :themes as well as the registry entry,
#     or the projection could drop or rename a key unnoticed.
#   - veil.ui.draw must decide nothing (bb shell-check, enforced). The
#     background color therefore has to arrive from veil.ui.view, not be
#     computed in draw!. The scenarios say "the frame's background color" and
#     leave the shape to the coder; terminal-cell-grid.feature now pins it
#     next to the draw commands built from the cell buffer.
#   - The 0-255 range is stricter than Java Veil's theme.schema.json, which
#     only requires an integer. No shipped Java theme is out of range, so
#     "loads unchanged" still holds, but a hand-edited Java theme with 300 in
#     it now fails where Java accepted it. Deliberate.
#   - The error messages come from the same clojure.spec translation
#     mod-loader.feature already flagged as regression-prone. "an integer from
#     0 to 255" is a new phrasing that translation has to produce.
#   - mods/core is the first content the game *requires* rather than merely
#     permits. Any run whose working directory has no mods/ now dies at
#     startup until the packaging work (Clarifications, Q4) lands; that work is part of this
#     issue precisely so no beta ships installers that cannot start.
#   - The installer fix (Q4) cannot be checked locally: `$APPDIR` expanding
#     inside `--java-options` on all three OSes, and mods/ landing in
#     `$APPDIR/mods`, are only proven by a workflow_dispatch run of
#     build-installers. Until that run, treat the fix as unverified. The
#     bare uberjar run from outside the repo root now exits at launch; the
#     README must say so.
#   - The "made active" step has no in-game caller until #17, so the active
#     theme's indirection is only exercised by specs. Without it, a mutant
#     that replaces the state lookup with the literal "core:default" survives.
#
# Open questions: none. Settled in three grilling rounds, recorded in
#   specs/intent/themes.md's Clarifications. Standard path: no
#   dependency-checker.edn change (Q1), no settings/save-format change, no
#   gate threshold change, so nothing here needs approval before Step 4.
