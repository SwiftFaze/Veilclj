# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-09-27T19:34:02.565801400Z","feature_name":"Terminal chrome widgets","feature_path":"specs/features/terminal-chrome-widgets.feature","background_hash":"74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b","implementation_hash":"unknown","scenarios":[{"index":0,"name":"A titled frame puts its title into the top edge","scenario_hash":"3b02dc50c12b0d2c6898e96ce8fb6d51912c5b0a5f7d9b52df2974b1c5ea3d70","mutation_count":16,"result":{"Total":16,"Killed":16,"Survived":0,"Errors":0},"tested_at":"2026-09-27T19:30:27.806621300Z"},{"index":2,"name":"A title too long for the frame is cut to fit","scenario_hash":"efa6f0eeaf5fce2b208c5f710105078c01b046f835f7eade1eee95630470500e","mutation_count":10,"result":{"Total":10,"Killed":10,"Survived":0,"Errors":0},"tested_at":"2026-09-27T19:30:27.806621300Z"},{"index":7,"name":"Each input has a keycap label","scenario_hash":"8bf19ab67b3a9a6135ce92c0fe8b8983eae5177af0374c1b13224abe01f9a645","mutation_count":14,"result":{"Total":14,"Killed":14,"Survived":0,"Errors":0},"tested_at":"2026-09-27T19:30:27.806621300Z"}]}
# acceptance-mutation-manifest-end

Feature: Terminal chrome widgets
  The static building blocks for dressing a screen like a Linux terminal
  editor: nano-style bars, whiptail-style boxed frames, and the small inline
  labels dialogs use. Every widget is a pure function that writes into the
  cell buffer and names theme keys, never colors, so the active theme colors
  all of them. widget-gallery.feature is the developer screen that shows
  every widget here with fake data.

  Covers: a frame (a single-line box whose optional title interrupts its top
    edge, and how a title too long for the frame is cut); the title bar (a
    full-width reverse-video row, left text and centered text); the status
    line (a centered bracketed message in reverse video, or a blank row); the
    hint bar (key-and-label hints laid out in equal slots, wrapping onto more
    rows docked at the bottom instead of overflowing, or drawing nothing with
    no hints); the keycap (a key's label in reverse video, and the label
    each input gets); inline accelerators (a highlighted letter in a label);
    and the badge and the chip in each semantic color, including a chip's
    focused look.
  Supersedes: nothing.
  Out of scope: activating an accelerator (widget-gallery.feature - these
    scenarios only draw the highlighted letter, they don't dispatch
    anything); tiling panes (#12); interactive widgets and moving focus
    between them (#13-#16), so a focused chip here is a rendering option, not
    something navigated to; retrofitting this chrome onto the main menu, map
    or options screens, which keep terminal-cell-grid.feature's border and
    layout; box styles other than single-line; the FOCUSED_BORDER and SHADOW
    theme keys; and how any of it looks, which is the human playtest's.
  QA: none - none of these widgets change game state or show up in a key
    script's event log; they only write into a buffer. widget-gallery.feature
    has the QA procedure that renders them all through a real screen.

  # --- Frame ---

  Scenario Outline: A titled frame puts its title into the top edge
    Given a blank buffer 20 columns by 5 rows
    When a frame at column 0, row 0, 20 wide and 5 high titled "Gallery" is drawn in BORDER on BACKGROUND
    Then the cell at column <col>, row 0 <result>

    Examples:
      | col | result                                      |
      | 0   | holds glyph U+250C in BORDER on BACKGROUND  |
      | 1   | holds glyph U+2500 in BORDER on BACKGROUND  |
      | 2   | holds " " in BORDER on BACKGROUND           |
      | 3   | holds "G" in BORDER on BACKGROUND           |
      | 9   | holds "y" in BORDER on BACKGROUND           |
      | 10  | holds " " in BORDER on BACKGROUND           |
      | 11  | holds glyph U+2500 in BORDER on BACKGROUND  |
      | 19  | holds glyph U+2510 in BORDER on BACKGROUND  |

  Scenario: A frame's other three edges are an ordinary box
    Given a blank buffer 20 columns by 5 rows
    When a frame at column 0, row 0, 20 wide and 5 high titled "Gallery" is drawn in BORDER on BACKGROUND
    Then the cell at column 0, row 4 holds glyph U+2514 in BORDER on BACKGROUND
    And the cell at column 19, row 4 holds glyph U+2518 in BORDER on BACKGROUND
    And the cell at column 0, row 2 holds glyph U+2502 in BORDER on BACKGROUND
    And the cell at column 5, row 4 holds glyph U+2500 in BORDER on BACKGROUND
    And the cell at column 5, row 2 is still blank

  Scenario Outline: A title too long for the frame is cut to fit
    Given a blank buffer 20 columns by 5 rows
    When a frame at column 0, row 0, <width> wide and 5 high titled "Gallery" is drawn in BORDER on BACKGROUND
    Then the top edge of the frame reads "<edge>"

    Examples:
      | width | edge                 |
      | 13    | +- Gallery -+        |
      | 12    | +- Galler -+         |
      | 10    | +- Gall -+           |
      | 7     | +- G -+              |
      | 6     | +----+               |

  Scenario: An untitled frame is the same as a box
    Given a blank buffer 20 columns by 5 rows
    When a frame at column 0, row 0, 20 wide and 5 high with no title is drawn in BORDER on BACKGROUND
    Then the buffer is the same as a box at column 0, row 0, 20 wide and 5 high drawn in BORDER on BACKGROUND

  # --- Title bar and status line ---

  Scenario Outline: The title bar fills its row in reverse video with left and centered text
    Given a blank buffer 80 columns by 24 rows
    When a title bar reading "VEIL" on the left and "Widget Gallery" in the center is drawn on row 0
    Then the cell at column <col>, row 0 <result>

    Examples:
      | col | result                                          |
      | 0   | holds " " in SELECTED_TEXT on SELECTED_HIGHLIGHT |
      | 1   | holds "V" in SELECTED_TEXT on SELECTED_HIGHLIGHT |
      | 33  | holds "W" in SELECTED_TEXT on SELECTED_HIGHLIGHT |
      | 46  | holds "y" in SELECTED_TEXT on SELECTED_HIGHLIGHT |
      | 79  | holds " " in SELECTED_TEXT on SELECTED_HIGHLIGHT |

  Scenario: The status line shows its message centered, bracketed, in reverse video
    Given a blank buffer 80 columns by 24 rows
    When a status line reading "Activated Guava" is drawn on row 22
    Then the text "[ Activated Guava ]" is at column 30, row 22
    And "[ Activated Guava ]" is drawn in SELECTED_TEXT on SELECTED_HIGHLIGHT
    And the cell at column 29, row 22 is still blank
    And the cell at column 49, row 22 is still blank

  Scenario: A status line with no message leaves its row blank
    Given a blank buffer 80 columns by 24 rows
    When a status line with no message is drawn on row 22
    Then the buffer is the same as a blank buffer 80 columns by 24 rows

  # --- Keycap ---

  Scenario Outline: Each input has a keycap label
    When the keycap label for <input> is asked for
    Then the label is "<label>"

    Examples:
      | input                     | label |
      | the back action           | Esc   |
      | the confirm action        | Enter |
      | the tab action            | Tab   |
      | the up action             | Up    |
      | the f12 action            | F12   |
      | the character a           | A     |
      | the character x with Ctrl | ^X    |

  Scenario: A keycap is its label in reverse video
    Given a blank buffer 10 columns by 1 row
    When a keycap for the back action is drawn at column 2, row 0
    Then the text "Esc" is at column 2, row 0
    And "Esc" is drawn in SELECTED_TEXT on SELECTED_HIGHLIGHT
    And the cell at column 5, row 0 is still blank

  # --- Hint bar ---

  Scenario Outline: The hint bar lays hints out in equal slots and docks at the bottom
    Given a blank buffer <cols> columns by 6 rows
    And the hints are Esc "Back", Ctrl+X "Exit", Ctrl+O "Write"
    When the hint bar is drawn
    Then the hint bar takes <bar_rows> rows
    And the keycap "<cap>" is at column <col>, row <row>
    And the label "<label>" is at column <label_col>, row <row> in NORMAL_TEXT on BACKGROUND

    Examples:
      | cols | bar_rows | cap | col | row | label | label_col |
      | 40   | 1        | Esc | 0   | 5   | Back  | 4         |
      | 40   | 1        | ^X  | 9   | 5   | Exit  | 12        |
      | 40   | 1        | ^O  | 18  | 5   | Write | 21        |
      | 20   | 2        | Esc | 0   | 4   | Back  | 4         |
      | 20   | 2        | ^X  | 9   | 4   | Exit  | 12        |
      | 20   | 2        | ^O  | 0   | 5   | Write | 3         |

  Scenario: A hint bar narrower than one slot puts one hint on each row
    Given a blank buffer 5 columns by 6 rows
    And the hints are Esc "Back", Ctrl+X "Exit", Ctrl+O "Write"
    When the hint bar is drawn
    Then the hint bar takes 3 rows
    And the text in row 3 reads "Esc B"
    And the text in row 5 reads "^O Wr"

  Scenario: A hint bar with no hints takes no rows and draws nothing
    Given a blank buffer 20 columns by 6 rows
    And there are no hints
    When the hint bar is drawn
    Then the hint bar takes 0 rows
    And the buffer is the same as a blank buffer 20 columns by 6 rows

  # --- Accelerators ---

  Scenario Outline: An accelerator letter is highlighted in its label
    Given a blank buffer 20 columns by 1 row
    When the label "<label>" with accelerator <letter> is written at column 0, row 0 in NORMAL_TEXT on BACKGROUND
    Then the cell at column <col>, row 0 holds "<glyph>" in ACCENT on BACKGROUND
    And the text in row 0 starts with "<label>"

    Examples:
      | label | letter | col | glyph |
      | Apple | a      | 0   | A     |
      | Guava | u      | 1   | u     |
      | Grape | G      | 0   | G     |

  Scenario: Only the accelerator letter is highlighted
    Given a blank buffer 20 columns by 1 row
    When the label "Guava" with accelerator u is written at column 0, row 0 in NORMAL_TEXT on BACKGROUND
    Then the cell at column 0, row 0 holds "G" in NORMAL_TEXT on BACKGROUND
    And the cell at column 2, row 0 holds "a" in NORMAL_TEXT on BACKGROUND
    And the cell at column 4, row 0 holds "a" in NORMAL_TEXT on BACKGROUND

  # --- Badge and chip ---

  Scenario Outline: A badge is its text, padded, on its semantic color
    Given a blank buffer 10 columns by 1 row
    When a <color> badge reading "<text>" is drawn at column 0, row 0
    Then the text " <text> " is at column 0, row 0
    And " <text> " is drawn in BACKGROUND on <color>

    Examples:
      | color   | text |
      | SUCCESS | 3    |
      | ERROR   | NEW  |
      | WARNING | 12   |
      | INFO    | 1    |
      | ACCENT  | NEW  |

  Scenario Outline: A chip is its tag in brackets, in its semantic color
    Given a blank buffer 10 columns by 1 row
    When a <color> chip reading "<tag>" is drawn at column 0, row 0
    Then the text "[<tag>]" is at column 0, row 0
    And "[<tag>]" is drawn in <color> on BACKGROUND

    Examples:
      | color   | tag   |
      | SUCCESS | done  |
      | ERROR   | fire  |
      | WARNING | low   |
      | INFO    | quest |
      | ACCENT  | new   |

  Scenario: A focused chip swaps its color onto its background
    Given a blank buffer 10 columns by 1 row
    When a focused ERROR chip reading "fire" is drawn at column 0, row 0
    Then "[fire]" is drawn in BACKGROUND on ERROR

# Non-goals:
#   - Navigating between chips or any other focus movement: #13-#16. The
#     focused chip is a rendering option a caller sets, not something moved
#     to with the keyboard.
#   - Chrome on the main menu, map and options screens. They keep
#     terminal-cell-grid.feature's border and layout untouched.
#   - Box styles other than single-line, FOCUSED_BORDER, SHADOW.
#   - An accelerator whose letter is not in its label: the label and letter
#     are always written by a caller, so a mismatch is a programmer error for
#     a unit spec, not a Gherkin scenario.
#
# Risks:
#   - The frame's top-edge glyphs are asserted in the "cut to fit" table
#     through a step that maps U+250C/U+2510 to "+" and U+2500 to "-", so the
#     file stays ASCII (terminal-cell-grid.feature's last Risk does the same).
#   - The keycap label table doubles as the source every hint bar example and
#     widget-gallery.feature's hint-bar scenario draw their labels from; a
#     changed label here changes them too.
#
# Open questions: see the grilling round in specs/intent/terminal-chrome-widgets.md.
