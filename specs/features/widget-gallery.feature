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
    widget group - Items, Badges, Chips, Message, each sized to its own
    content with matching left/right interior padding - hint bar) in
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
    And the cell at column 0, row 9 holds glyph U+250C in WINDOW_BORDER on BACKGROUND
    And the text "Badges" is at column 3, row 9
    And the cell at column 34, row 13 holds glyph U+2518 in WINDOW_BORDER on BACKGROUND
    And the cell at column 0, row 14 holds glyph U+250C in WINDOW_BORDER on BACKGROUND
    And the text "Chips" is at column 3, row 14
    And the cell at column 34, row 18 holds glyph U+2518 in WINDOW_BORDER on BACKGROUND
    And the cell at column 0, row 19 holds glyph U+250C in WINDOW_BORDER on BACKGROUND
    And the text "Message" is at column 3, row 19
    And the cell at column 25, row 21 holds glyph U+2518 in WINDOW_BORDER on BACKGROUND
    And the keycap "Esc" is at column 0, row 23

  Scenario: The Gallery's hint bar lists exactly its own bindings, in order
    Given the game is on the widget gallery screen
    When the screen is rendered into a grid of 80 columns by 24 rows
    Then the hints shown are Esc "Close", A "Apple", B "Banana", G "Grape", U "Guava"

  Scenario: Activating an item shows in the Gallery's Message box
    Given the game is on the widget gallery screen
    And the player types u
    When the screen is rendered into a grid of 80 columns by 24 rows
    Then the text "[ Activated Guava ]" is at column 3, row 20
    And "[ Activated Guava ]" is drawn in SELECTED_TEXT on SELECTED_HIGHLIGHT

  Scenario Outline: The Gallery shows a badge and a chip in each semantic color
    Given the game is on the widget gallery screen
    When the screen is rendered into a grid of 80 columns by 24 rows
    Then the text " <abbr> " is at column <col>, row 11
    And " <abbr> " is drawn in BACKGROUND on <color>
    And the text "[<abbr>]" is at column <col>, row 16
    And "[<abbr>]" is drawn in <color> on BACKGROUND

    Examples:
      | color   | abbr | col |
      | SUCCESS | SUC  | 3   |
      | ERROR   | ERR  | 9   |
      | WARNING | WRN  | 15  |
      | INFO    | INF  | 21  |
      | ACCENT  | ACC  | 27  |

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
#   - Layout revised three times after human playtest (Clarifications).
#     First: the single unlabeled "Chrome" frame (rows 1-21) became one
#     titled frame per widget group - Items, Badges, Chips. Second: each
#     frame sized to its own content (2-column left/right, 1-row top/bottom
#     interior padding) instead of stretched to 80 columns. Third: the
#     status message got its own "Message" box too, which required removing
#     the blank separator rows between frames (zero spare rows left in the
#     24-row screen once four padded boxes are laid out) and giving Message
#     no internal top/bottom padding (border/content/border only - the one
#     component that doesn't match the others' interior padding, purely
#     because there was no row left to give it). Current geometry, all four
#     boxes left-aligned at column 0, stacked with no gap between them:
#     Items width 12 rows 1-8 (content rows 3-6), Badges width 35 rows 9-13
#     (content row 11), Chips width 35 rows 14-18 (content row 16), Message
#     width 26 rows 19-21 (content row 20, left-aligned not centered - see
#     intent Clarifications), row 22 spare (breathing room before the hint
#     bar), row 23 hint bar. The item list's own column position inside its
#     frame is still deliberately unpinned beyond "one per interior row"
#     (see Non-goals).
#
# Open questions: see the grilling round in specs/intent/terminal-chrome-widgets.md.
