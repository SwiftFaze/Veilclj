# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-09-27T19:34:19.815860900Z","feature_name":"Widget Gallery","feature_path":"specs/features/widget-gallery.feature","background_hash":"c278d9acbaf415d175b5b358c977d9702949c29bb8d5724ff7d60e6fd1f179e6","implementation_hash":"unknown","scenarios":[{"index":2,"name":"Esc in the Widget Gallery returns to the main menu with its selection kept","scenario_hash":"4f1f7b0e6582bf4cb1af5112f7cef2051932659f79d4813ced0cbc6184df9528","mutation_count":3,"result":{"Total":3,"Killed":3,"Survived":0,"Errors":0},"tested_at":"2026-09-27T19:30:44.528057700Z"},{"index":5,"name":"Keys that are no item's accelerator change nothing in the Gallery","scenario_hash":"dd2944682cc0b68d06888138ad2ace1fb3a86f7dd559e9a9b56579e08a6a3852","mutation_count":5,"result":{"Total":5,"Killed":5,"Survived":0,"Errors":0},"tested_at":"2026-09-27T19:30:44.528057700Z"},{"index":9,"name":"The Gallery shows a badge and a chip in each semantic color","scenario_hash":"3d3c8d39b82eecdfafafb89d0385243d67fe52be5c0a137d72dc71b9d45ae475","mutation_count":15,"result":{"Total":15,"Killed":15,"Survived":0,"Errors":0},"tested_at":"2026-09-27T19:30:44.528057700Z"}]}
# acceptance-mutation-manifest-end

Feature: Widget Gallery
  A developer screen that shows every widget from terminal-chrome-widgets.feature
  with fake data, so they can be playtested before any real screen uses them.
  Opened with F12 from the main menu, closed with Esc; not a player-facing
  menu item.

  Covers: F12 opening the Gallery from the main menu (its translation to an
    input is keyboard-input.feature's), and doing nothing from any other
    screen (including the Gallery itself); Esc closing the Gallery back to
    the main menu with its selection kept; the Gallery not appearing in the
    menu items; typing a fake item's accelerator showing an activation
    message in its own boxed Message panel, and every other key changing
    nothing; the Gallery's own chrome (title bar, one titled frame per
    widget group - Items on the left, Badges/Chips/Message stacked in a
    second column to its right - each sized to its own content with
    matching left/right and top/bottom interior padding - hint bar) in
    place of the whole-grid border; the hint bar listing exactly the Gallery's own
    bindings; a badge and a chip shown in every semantic color, at fixed
    positions; and every cell the Gallery draws naming a color the active
    theme defines.
  Supersedes: terminal-cell-grid.feature's "every screen has a single-line
    border around the whole grid": the Gallery is the one screen without it,
    because its title bar and hint bar take the rows the border would.
  Out of scope: what F12 translates to (keyboard-input.feature); the
    widgets' own rendering rules (terminal-chrome-widgets.feature draws and
    pins those; this file only asserts they appear assembled); navigating
    between the Gallery's chips (#13-#16); retrofitting this chrome onto the
    main menu, map or options screens; and how the Gallery looks, which is
    the human playtest's.
  QA: specs/qa/widget-gallery.keys and .edn press F12 on the main menu and
    Esc in the Gallery, checking both screen changes through the real key
    path.

  Background:
    Given the game has just started

  # --- Getting in and out ---

  Scenario: F12 on the main menu opens the Widget Gallery
    Given the selected menu item is Options
    When the player presses F12
    Then the current screen is the widget gallery

  Scenario: The Widget Gallery is not a menu item
    Then the menu items are New Game, Options, Quit

  Scenario Outline: Esc in the Widget Gallery returns to the main menu with its selection kept
    Given the selected menu item is <item>
    And the player presses F12
    When the player presses Esc
    Then the current screen is the main menu
    And the selected menu item is <item>

    Examples:
      | item     |
      | New Game |
      | Options  |
      | Quit     |

  Scenario Outline: F12 anywhere but the main menu does nothing
    Given the game is on the <screen> screen
    When the player presses F12
    Then the current screen is the <screen> screen

    Examples:
      | screen         |
      | map            |
      | options        |
      | widget gallery |

  # --- Accelerators ---

  Scenario Outline: Typing an item's accelerator activates it
    Given the game is on the widget gallery screen
    When the player types <char>
    Then the gallery's status message is "Activated <item>"
    And the current screen is the widget gallery

    Examples:
      | char | item   |
      | a    | Apple  |
      | b    | Banana |
      | g    | Grape  |
      | u    | Guava  |
      | U    | Guava  |

  Scenario Outline: Keys that are no item's accelerator change nothing in the Gallery
    Given the game is on the widget gallery screen
    When the player <does>
    Then the gallery has no status message
    And the current screen is the widget gallery

    Examples:
      | does                       |
      | types z                    |
      | types w                    |
      | types u with Ctrl held     |
      | presses Enter              |
      | presses Down               |

  # --- What it shows ---

  Scenario: The Gallery is dressed in terminal chrome instead of the whole-grid border
    Given the game is on the widget gallery screen
    When the screen is rendered into a grid of 80 columns by 24 rows
    Then the text "Widget Gallery" is at column 33, row 0
    And the cell at column 0, row 0 has the background SELECTED_HIGHLIGHT
    And the cell at column 0, row 1 holds glyph U+250C in WINDOW_BORDER on BACKGROUND
    And the cell at column 11, row 8 holds glyph U+2518 in WINDOW_BORDER on BACKGROUND
    And the text "Items" is at column 3, row 1
    And the cell at column 13, row 1 holds glyph U+250C in WINDOW_BORDER on BACKGROUND
    And the text "Badges" is at column 16, row 1
    And the cell at column 47, row 5 holds glyph U+2518 in WINDOW_BORDER on BACKGROUND
    And the cell at column 13, row 6 holds glyph U+250C in WINDOW_BORDER on BACKGROUND
    And the text "Chips" is at column 16, row 6
    And the cell at column 47, row 10 holds glyph U+2518 in WINDOW_BORDER on BACKGROUND
    And the cell at column 13, row 11 holds glyph U+250C in WINDOW_BORDER on BACKGROUND
    And the text "Message" is at column 16, row 11
    And the cell at column 38, row 15 holds glyph U+2518 in WINDOW_BORDER on BACKGROUND
    And the keycap "Esc" is at column 0, row 23

  Scenario: The Gallery's hint bar lists exactly its own bindings, in order
    Given the game is on the widget gallery screen
    When the screen is rendered into a grid of 80 columns by 24 rows
    Then the hints shown are Esc "Close", A "Apple", B "Banana", G "Grape", U "Guava"

  Scenario: Activating an item shows in the Gallery's Message box
    Given the game is on the widget gallery screen
    And the player types u
    When the screen is rendered into a grid of 80 columns by 24 rows
    Then the text " [ Activated Guava ] " is at column 15, row 13
    And " [ Activated Guava ] " is drawn in SELECTED_TEXT on SELECTED_HIGHLIGHT
    And the cell at column 15, row 12 has the background SELECTED_HIGHLIGHT
    And the cell at column 35, row 12 has the background SELECTED_HIGHLIGHT
    And the cell at column 15, row 14 has the background SELECTED_HIGHLIGHT
    And the cell at column 35, row 14 has the background SELECTED_HIGHLIGHT

  Scenario Outline: The Gallery shows a badge and a chip in each semantic color
    Given the game is on the widget gallery screen
    When the screen is rendered into a grid of 80 columns by 24 rows
    Then the text " <abbr> " is at column <col>, row 3
    And " <abbr> " is drawn in BACKGROUND on <color>
    And the text "[<abbr>]" is at column <col>, row 8
    And "[<abbr>]" is drawn in <color> on BACKGROUND

    Examples:
      | color   | abbr | col |
      | SUCCESS | SUC  | 16  |
      | ERROR   | ERR  | 22  |
      | WARNING | WRN  | 28  |
      | INFO    | INF  | 34  |
      | ACCENT  | ACC  | 40  |

  Scenario: Every cell of the Gallery names a color the active theme defines
    Given the game is on the widget gallery screen
    When the screen is rendered into a grid of 80 columns by 24 rows
    And the buffer is turned into draw commands for cells 12 by 25 pixels
    Then building the draw commands succeeds

# Non-goals:
#   - Re-pinning how a frame, title bar, status line, keycap, hint bar,
#     accelerator, badge or chip renders in isolation:
#     terminal-chrome-widgets.feature owns those rules; this file only checks
#     they show up correctly assembled on a real screen.
#   - Navigating between the Gallery's chips or any other focus movement:
#     #13-#16.
#   - Chrome on the main menu, map and options screens: out of scope for this
#     issue, they keep terminal-cell-grid.feature's border and layout.
#   - The Gallery's exact layout inside its frame beyond what the scenarios
#     pin; how it looks is the playtest's.
#
# Risks:
#   - Single answer (docs/architecture.md). The hint bar must be built from
#     the same bindings veil.game.state dispatches on, not a parallel list in
#     veil.ui, or the two drift. Likewise the Gallery's accelerator letters
#     live with its items in veil.game; veil.ui only highlights the letter it
#     is told. "The Gallery's hint bar lists exactly its own bindings" is
#     what pins it, together with the accelerator scenarios.
#   - The Gallery's status message is new state, under a :gallery key rather
#     than :menu, so Esc and F12 leave the menu selection alone.
#   - Added after #11's playtest: glyph commands vertically centering within
#     their cell (terminal-cell-grid.feature's new "glyph command carries the
#     cell's pixel size" scenario, veil.ui.draw) fixes text that floated near
#     the top of colored-background cells - most visible on the badges and
#     the Message box, both reverse-video-adjacent widgets. No scenario here
#     changes for it; the fix lives entirely in the shared rendering pipeline.
#   - F12's translation to an input (and F1 through F11 staying untranslated)
#     is keyboard-input.feature's, not this file's; F12 is added there so
#     one file owns "what a key translates to". It's also added to the QA
#     script vocabulary (veil.ui.qa.script/special-keys) so the QA run here
#     can reach the Gallery; deterministic-keyboard-qa.feature's key list
#     grows by one.
#   - The Gallery screen adds a value (:widget-gallery) to :screen/changed's
#     :from/:to; no new event kind.
#   - Layout revised four times after human playtest (Clarifications).
#     First: the single unlabeled "Chrome" frame (rows 1-21) became one
#     titled frame per widget group - Items, Badges, Chips. Second: each
#     frame sized to its own content (2-column left/right, 1-row top/bottom
#     interior padding) instead of stretched to 80 columns. Third: the
#     status message got its own "Message" box too, which (stacked in the
#     same single column as everything else) left no row budget for its own
#     padding. Fourth: realized the single-column stack was the wrong shape
#     - every box so far used at most 35 of the 80 available columns, so a
#     second column (Badges/Chips/Message, stacked to the right of Items)
#     gives every box, including Message, full consistent padding with rows
#     to spare, rather than trading padding away for row budget. Current
#     geometry, 2-column left/right and 1-row top/bottom interior padding
#     everywhere, no exceptions: Items width 12 rows 1-8 columns 0-11
#     (content rows 3-6); Badges width 35 rows 1-5 columns 13-47 (content
#     row 3, badge columns 16/22/28/34/40); Chips width 35 rows 6-10 columns
#     13-47 (content row 8, same columns as Badges); Message width 26 rows
#     11-15 columns 13-38 (content row 13, text left-aligned at column 16,
#     not centered - see intent Clarifications). Rows 16-22 and columns
#     48-79 are free space, left for #12-#16's own sections (the issue's
#     original plan). Row 0 title bar, row 23 hint bar, both unchanged. The
#     item list's own column position inside its frame is still
#     deliberately unpinned beyond "one per interior row" (see Non-goals).
#
# Open questions: see the grilling round in specs/intent/terminal-chrome-widgets.md.
