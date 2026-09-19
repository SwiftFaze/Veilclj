# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-09-19T20:53:11.628044200Z","feature_name":"Terminal cell grid","feature_path":"specs/features/terminal-cell-grid.feature","background_hash":"74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b","implementation_hash":"unknown","scenarios":[{"index":8,"name":"Filling a rectangle sets every cell inside it and no cell outside","scenario_hash":"57bfcfc7e7392eb6c28c383f1ee163a79b547e16df6f47476449a34c82801d68","mutation_count":24,"result":{"Total":24,"Killed":24,"Survived":0,"Errors":0},"tested_at":"2026-09-19T20:52:03.389668800Z"},{"index":11,"name":"A box is drawn with single-line box-drawing glyphs on its edge","scenario_hash":"b1698854d8f64020a8de878ec6f9ede83964ec00f6544d9ef9437d374acc7432","mutation_count":24,"result":{"Total":24,"Killed":24,"Survived":0,"Errors":0},"tested_at":"2026-09-19T20:52:03.389668800Z"},{"index":17,"name":"A cell's pixel position follows from its column and row","scenario_hash":"bb5fb51767b81f399f68daf92ecdb5e38138707d34c3de5ec91e9819532093fa","mutation_count":12,"result":{"Total":12,"Killed":12,"Survived":0,"Errors":0},"tested_at":"2026-09-19T20:52:03.389668800Z"},{"index":25,"name":"The main menu puts each line at its row, centered in an 80-column grid","scenario_hash":"09ae4960e3c5d0cef4aa92e8cffdcfa9bd027c4c0759d752ad0aa0ffb9db34f5","mutation_count":15,"result":{"Total":15,"Killed":15,"Survived":0,"Errors":0},"tested_at":"2026-09-19T20:52:03.389668800Z"},{"index":26,"name":"The selected menu item is drawn in reverse video and nothing else is","scenario_hash":"11741e951c5c475e0a28b2db64b414fd054e0d71b4bba89d22c879d67647cb2f","mutation_count":6,"result":{"Total":6,"Killed":6,"Survived":0,"Errors":0},"tested_at":"2026-09-19T20:52:03.389668800Z"},{"index":28,"name":"The map and options screens put each line at its row, centered in an 80-column grid","scenario_hash":"fdde9ea2c8b2ecb87523914d9f1908539a9e3d93069930ae03fe31cb3e6b6b91","mutation_count":16,"result":{"Total":16,"Killed":16,"Survived":0,"Errors":0},"tested_at":"2026-09-19T20:52:03.389668800Z"},{"index":31,"name":"The border follows the grid's size","scenario_hash":"2eea91d511d0dbe47ca011cf33fa932a969329f0d734ba35f565fa54ecb46088","mutation_count":12,"result":{"Total":12,"Killed":12,"Survived":0,"Errors":0},"tested_at":"2026-09-19T20:52:03.389668800Z"},{"index":32,"name":"The border leaves the cells just inside it blank","scenario_hash":"2cbe38f9c1e0f93a646eab04869071a968e17f5f2b241f018d9ec3a1d965dfe4","mutation_count":3,"result":{"Total":3,"Killed":3,"Survived":0,"Errors":0},"tested_at":"2026-09-19T20:52:03.389668800Z"},{"index":33,"name":"Every screen renders into a grid of the requested size","scenario_hash":"64ce0c89f5e5dac06fd28ba12660d4bd047d847353da86bc073d510f82a96962","mutation_count":3,"result":{"Total":3,"Killed":3,"Survived":0,"Errors":0},"tested_at":"2026-09-19T20:52:03.389668800Z"}]}
# acceptance-mutation-manifest-end

Feature: Terminal cell grid
  The game draws into a fixed grid of character cells, like a terminal. A cell
  holds one glyph, a foreground color and a background color, each named by a
  theme key (BORDER, SELECTED_TEXT, ...). The grid is as many whole cells as
  fit the window, never fewer than 80 columns by 24 rows. Screens write into a
  pure buffer of cells; a pure step turns the buffer into draw commands with
  pixel positions and RGB colors resolved against the active theme; the Quil
  layer only carries those commands out. Columns and rows are numbered from 0,
  starting at the top left.

  Covers: the cell buffer (a blank buffer, writing text, filling a rectangle,
    drawing a single-line box, clipping at the grid's edges, buffers being
    values that a write does not change), the grid size derived from the window
    size and the cell size (whole cells only, the 80x24 minimum), turning a
    buffer into draw commands (pixel position from column and row, theme keys
    resolved to colors, blank cells adding nothing, a key the theme lacks
    failing loudly), the main menu, map and options screens drawn through the
    buffer (position, reverse video on the selected menu item, staying centered
    when the grid is wider than 80 columns), and the single-line border
    around the whole grid on every screen.
  Supersedes: how the three screens are laid out and colored. The pixel `:x`,
    `:y` and `:color` commands of veil.ui.view/frame and `view/background` go
    away, so the three "draw command" scenarios of thin-quil-shell.feature are
    replaced by the draw-command scenarios here, and the two "what the player
    sees" scenarios of themes.feature are re-worded to the buffer (the selected
    item is now reverse video, not text in SELECTED_HIGHLIGHT). It also
    overturns game_window.feature's "resizing" exclusion: the window is now
    resizable, and the grid follows it.
  Out of scope: frames around content, title bars, hint bars and every widget
    (#11; the one-cell border around the whole grid is the exception and is
    covered here, and #11's frames may replace it); more than
    one box style; text wrapping; the font itself (which file, how a mod
    supplies it, what a bad one does), which is mod content with its own rules;
    the Quil calls themselves (opening the resizable window, loading the font,
    reading the font's cell size, drawing the commands): they need a live window
    and belong to the human playtest and `bb qa`; choosing the font's pixel
    size, which is tuned by eye in the playtest; choosing a theme (#17); and any
    change to what the screens say or to the keys that move between them
    (main-menu.feature).
  QA: none - drawing does not show in a key script's event log. The existing
    main-menu QA run still drives every screen through the new blit, and the
    human playtest checks how it looks and resizes.

  # --- The buffer ---

  Scenario Outline: A new buffer is all blank cells of the size asked for
    When a blank buffer <cols> columns by <rows> rows is made
    Then the buffer has <cols> columns and <rows> rows
    And every cell holds a space in NORMAL_TEXT on BACKGROUND

    Examples:
      | cols | rows |
      | 80   | 24   |
      | 132  | 43   |
      | 1    | 1    |

  Scenario: Writing text puts one glyph in each cell from the starting cell on
    Given a blank buffer 10 columns by 3 rows
    When "Hi!" is written at column 2, row 1 in ACCENT on SELECTED_HIGHLIGHT
    Then the cell at column 2, row 1 holds "H" in ACCENT on SELECTED_HIGHLIGHT
    And the cell at column 3, row 1 holds "i" in ACCENT on SELECTED_HIGHLIGHT
    And the cell at column 4, row 1 holds "!" in ACCENT on SELECTED_HIGHLIGHT

  Scenario Outline: Writing text leaves every other cell as it was
    Given a blank buffer 10 columns by 3 rows
    When "Hi!" is written at column 2, row 1 in ACCENT on SELECTED_HIGHLIGHT
    Then the cell at column <col>, row <row> is still blank

    Examples:
      | col | row |
      | 1   | 1   |
      | 5   | 1   |
      | 2   | 0   |
      | 2   | 2   |
      | 0   | 0   |

  Scenario: Writing text over earlier text replaces those cells
    Given a blank buffer 10 columns by 1 row
    And "abcd" is written at column 0, row 0 in NORMAL_TEXT on BACKGROUND
    When "XY" is written at column 1, row 0 in ERROR on BACKGROUND
    Then the text in row 0 reads "aXYd      "
    And the cell at column 1, row 0 holds "X" in ERROR on BACKGROUND
    And the cell at column 3, row 0 holds "d" in NORMAL_TEXT on BACKGROUND

  Scenario: Text that runs past the right edge is clipped
    Given a blank buffer 10 columns by 3 rows
    When "Hello" is written at column 8, row 1 in NORMAL_TEXT on BACKGROUND
    Then the text in row 1 reads "        He"
    And the buffer still has 10 columns and 3 rows

  Scenario: Text that starts left of the left edge shows the part that is inside
    Given a blank buffer 10 columns by 3 rows
    When "Hello" is written at column -2, row 0 in NORMAL_TEXT on BACKGROUND
    Then the text in row 0 reads "llo       "

  Scenario Outline: Text written entirely outside the grid changes nothing
    Given a blank buffer 10 columns by 3 rows
    When "Hi" is written at column <col>, row <row> in ERROR on BACKGROUND
    Then the buffer is the same as a blank buffer 10 columns by 3 rows

    Examples:
      | col | row |
      | 10  | 0   |
      | 0   | 3   |
      | 0   | -1  |
      | -2  | 0   |
      | 3   | 100 |

  Scenario: Writing to a buffer leaves the buffer it was given unchanged
    Given a blank buffer 10 columns by 3 rows
    When "Hi" is written at column 0, row 0 in ERROR on BACKGROUND
    Then the buffer that was written to is the same as a blank buffer 10 columns by 3 rows

  Scenario Outline: Filling a rectangle sets every cell inside it and no cell outside
    Given a blank buffer 10 columns by 5 rows
    When the rectangle at column 2, row 1, 3 wide and 2 high is filled with SELECTED_HIGHLIGHT
    Then the cell at column <col>, row <row> <result>

    Examples:
      | col | row | result                                     |
      | 2   | 1   | holds a space in NORMAL_TEXT on SELECTED_HIGHLIGHT |
      | 4   | 1   | holds a space in NORMAL_TEXT on SELECTED_HIGHLIGHT |
      | 2   | 2   | holds a space in NORMAL_TEXT on SELECTED_HIGHLIGHT |
      | 4   | 2   | holds a space in NORMAL_TEXT on SELECTED_HIGHLIGHT |
      | 1   | 1   | is still blank                             |
      | 5   | 1   | is still blank                             |
      | 2   | 0   | is still blank                             |
      | 2   | 3   | is still blank                             |

  Scenario: Filling replaces the glyphs that were in the rectangle
    Given a blank buffer 10 columns by 1 row
    And "abcdef" is written at column 0, row 0 in NORMAL_TEXT on BACKGROUND
    When the rectangle at column 1, row 0, 2 wide and 1 high is filled with ACCENT
    Then the text in row 0 reads "a  def    "

  Scenario: A rectangle that reaches past the edge is clipped
    Given a blank buffer 10 columns by 5 rows
    When the rectangle at column 8, row 3, 5 wide and 5 high is filled with ACCENT
    Then the cell at column 9, row 4 holds a space in NORMAL_TEXT on ACCENT
    And the cell at column 7, row 4 is still blank
    And the buffer still has 10 columns and 5 rows

  Scenario Outline: A box is drawn with single-line box-drawing glyphs on its edge
    Given a blank buffer 10 columns by 6 rows
    When a box at column 1, row 1, 5 wide and 4 high is drawn in BORDER on BACKGROUND
    Then the cell at column <col>, row <row> holds glyph <glyph> in BORDER on BACKGROUND

    Examples:
      | col | row | glyph  |
      | 1   | 1   | U+250C |
      | 5   | 1   | U+2510 |
      | 1   | 4   | U+2514 |
      | 5   | 4   | U+2518 |
      | 3   | 1   | U+2500 |
      | 3   | 4   | U+2500 |
      | 1   | 2   | U+2502 |
      | 5   | 3   | U+2502 |

  Scenario Outline: A box leaves the cells inside it and outside it as they were
    Given a blank buffer 10 columns by 6 rows
    And "xyz" is written at column 2, row 2 in NORMAL_TEXT on BACKGROUND
    When a box at column 1, row 1, 5 wide and 4 high is drawn in BORDER on BACKGROUND
    Then the cell at column <col>, row <row> <result>

    Examples:
      | col | row | result                                    |
      | 2   | 2   | holds "x" in NORMAL_TEXT on BACKGROUND    |
      | 4   | 2   | holds "z" in NORMAL_TEXT on BACKGROUND    |
      | 3   | 3   | is still blank                            |
      | 0   | 1   | is still blank                            |
      | 6   | 4   | is still blank                            |
      | 3   | 5   | is still blank                            |

  Scenario Outline: A box too small to have an inside and an outside draws nothing
    Given a blank buffer 10 columns by 6 rows
    When a box at column 1, row 1, <width> wide and <height> high is drawn in BORDER on BACKGROUND
    Then the buffer is the same as a blank buffer 10 columns by 6 rows

    Examples:
      | width | height |
      | 1     | 4      |
      | 5     | 1      |
      | 1     | 1      |
      | 0     | 3      |
      | 3     | 0      |

  Scenario: A box that reaches past the edge keeps the part that is inside
    Given a blank buffer 10 columns by 6 rows
    When a box at column 7, row 3, 6 wide and 5 high is drawn in BORDER on BACKGROUND
    Then the cell at column 7, row 3 holds glyph U+250C in BORDER on BACKGROUND
    And the cell at column 9, row 3 holds glyph U+2500 in BORDER on BACKGROUND
    And the cell at column 7, row 5 holds glyph U+2502 in BORDER on BACKGROUND
    And the buffer still has 10 columns and 6 rows

  # --- The grid size ---

  Scenario Outline: The grid is as many whole cells as fit the window
    When the grid size is worked out for a window <width> by <height> pixels with cells <cell_w> by <cell_h> pixels
    Then the grid is <cols> columns by <rows> rows

    Examples:
      | width | height | cell_w | cell_h | cols | rows |
      | 960   | 600    | 12     | 25     | 80   | 24   |
      | 1200  | 600    | 12     | 25     | 100  | 24   |
      | 960   | 750    | 12     | 25     | 80   | 30   |
      | 1920  | 1080   | 12     | 25     | 160  | 43   |
      | 971   | 619    | 12     | 25     | 80   | 24   |
      | 972   | 625    | 12     | 25     | 81   | 25   |

  Scenario Outline: The grid is never smaller than 80 columns by 24 rows
    When the grid size is worked out for a window <width> by <height> pixels with cells <cell_w> by <cell_h> pixels
    Then the grid is <cols> columns by <rows> rows

    Examples:
      | width | height | cell_w | cell_h | cols | rows |
      | 600   | 300    | 12     | 25     | 80   | 24   |
      | 1200  | 300    | 12     | 25     | 100  | 24   |
      | 600   | 750    | 12     | 25     | 80   | 30   |
      | 959   | 599    | 12     | 25     | 80   | 24   |

  # --- From buffer to draw commands ---

  Scenario Outline: A cell's pixel position follows from its column and row
    Given a blank buffer 80 columns by 24 rows
    And "A" is written at column <col>, row <row> in NORMAL_TEXT on BACKGROUND
    When the buffer is turned into draw commands for cells 12 by 25 pixels
    Then there is a glyph command for "A" at x <x> and y <y>

    Examples:
      | col | row | x   | y   |
      | 0   | 0   | 0   | 0   |
      | 3   | 2   | 36  | 50  |
      | 79  | 23  | 948 | 575 |

  Scenario: A glyph is drawn in the color its foreground key names in the active theme
    Given the active theme has ACCENT 238,179,146
    And a blank buffer 80 columns by 24 rows
    And "A" is written at column 3, row 2 in ACCENT on BACKGROUND
    When the buffer is turned into draw commands for cells 12 by 25 pixels
    Then the glyph command for "A" has the color 238,179,146

  Scenario: A cell whose background is not the frame's background gets a rectangle of the cell's size
    Given the active theme has SELECTED_HIGHLIGHT 192,192,192
    And a blank buffer 80 columns by 24 rows
    And "A" is written at column 3, row 2 in NORMAL_TEXT on SELECTED_HIGHLIGHT
    When the buffer is turned into draw commands for cells 12 by 25 pixels
    Then there is a rectangle command at x 36 and y 50, 12 wide and 25 high, with the color 192,192,192

  Scenario: A space draws its background and no glyph
    Given a blank buffer 80 columns by 24 rows
    And the rectangle at column 3, row 2, 1 wide and 1 high is filled with ACCENT
    When the buffer is turned into draw commands for cells 12 by 25 pixels
    Then there is one rectangle command
    And there is no glyph command

  Scenario: A blank buffer adds no draw commands, only the frame's background color
    Given the active theme has BACKGROUND 5,5,5
    And a blank buffer 80 columns by 24 rows
    When the buffer is turned into draw commands for cells 12 by 25 pixels
    Then there are no draw commands
    And the frame's background color is 5,5,5

  Scenario: Reverse video is the selection colors swapped onto the cell
    Given the active theme has SELECTED_HIGHLIGHT 192,192,192 and SELECTED_TEXT 0,0,0
    And a blank buffer 80 columns by 24 rows
    And "Q" is written at column 0, row 0 in SELECTED_TEXT on SELECTED_HIGHLIGHT
    When the buffer is turned into draw commands for cells 12 by 25 pixels
    Then the glyph command for "Q" has the color 0,0,0
    And the rectangle command at x 0 and y 0 has the color 192,192,192

  Scenario: Recoloring the theme recolors the draw commands and not the buffer
    Given the active theme has BORDER 192,192,192
    And a blank buffer 80 columns by 24 rows
    And "A" is written at column 0, row 0 in BORDER on BACKGROUND
    When the buffer is turned into draw commands for cells 12 by 25 pixels
    And the theme's BORDER is changed to 255,176,0
    And the buffer is turned into draw commands for cells 12 by 25 pixels
    Then the glyph command for "A" has the color 255,176,0
    And the buffer is unchanged

  Scenario Outline: A cell naming a color the theme does not have fails loudly
    Given a blank buffer 80 columns by 24 rows
    And "A" is written at column 0, row 0 in <fg> on <bg>
    When the buffer is turned into draw commands for cells 12 by 25 pixels
    Then building the draw commands fails naming the color key "BORDR"

    Examples:
      | fg    | bg         |
      | BORDR | BACKGROUND |
      | BORDER | BORDR      |

  # --- The screens through the buffer ---

  Scenario Outline: The main menu puts each line at its row, centered in an 80-column grid
    Given the main menu with New Game selected
    When the screen is rendered into a grid of 80 columns by 24 rows
    Then the text "<text>" is at column <col>, row <row>

    Examples:
      | text                                        | col | row |
      | VEIL                                        | 38  | 2   |
      | New Game                                    | 36  | 4   |
      | Options                                     | 36  | 6   |
      | Quit                                        | 38  | 8   |
      | Use Up/Down or W/S to move, Enter to select | 18  | 12  |

  Scenario Outline: The selected menu item is drawn in reverse video and nothing else is
    Given the main menu with <item> selected
    When the screen is rendered into a grid of 80 columns by 24 rows
    Then "<item>" is drawn in SELECTED_TEXT on SELECTED_HIGHLIGHT
    And "<other>" is drawn in NORMAL_TEXT on BACKGROUND
    And "VEIL" is drawn in NORMAL_TEXT on BACKGROUND

    Examples:
      | item     | other    |
      | New Game | Options  |
      | Options  | Quit     |
      | Quit     | New Game |

  Scenario: The highlight covers exactly the selected item's cells
    Given the main menu with Options selected
    When the screen is rendered into a grid of 80 columns by 24 rows
    Then the cell at column 35, row 6 is still blank
    And the cell at column 36, row 6 has the background SELECTED_HIGHLIGHT
    And the cell at column 42, row 6 has the background SELECTED_HIGHLIGHT
    And the cell at column 43, row 6 is still blank

  Scenario Outline: The map and options screens put each line at its row, centered in an 80-column grid
    Given the game is on the <screen> screen
    When the screen is rendered into a grid of 80 columns by 24 rows
    Then the text "<text>" is at column <col>, row <row>

    Examples:
      | screen  | text     | col | row |
      | map     | @        | 39  | 6   |
      | map     | Esc: menu | 35 | 20  |
      | options | Options  | 36  | 4   |
      | options | Esc: back | 35 | 20  |

  Scenario Outline: A wider or taller grid keeps the text centered across and anchored to the top
    Given the main menu with New Game selected
    When the screen is rendered into a grid of <cols> columns by <rows> rows
    Then the text "<text>" is at column <col>, row <row>

    Examples:
      | cols | rows | text                                        | col | row |
      | 100  | 30   | VEIL                                        | 48  | 2   |
      | 100  | 30   | New Game                                    | 46  | 4   |
      | 100  | 30   | Use Up/Down or W/S to move, Enter to select | 28  | 12  |
      | 81   | 24   | VEIL                                        | 38  | 2   |
      | 160  | 43   | Quit                                        | 78  | 8   |

  Scenario Outline: Every screen has a single-line border around the whole grid
    Given the game is on the <screen> screen
    When the screen is rendered into a grid of 80 columns by 24 rows
    Then the cell at column <col>, row <row> holds glyph <glyph> in WINDOW_BORDER on BACKGROUND

    Examples:
      | screen    | col | row | glyph  |
      | main menu | 0   | 0   | U+250C |
      | main menu | 79  | 0   | U+2510 |
      | main menu | 0   | 23  | U+2514 |
      | main menu | 79  | 23  | U+2518 |
      | main menu | 40  | 0   | U+2500 |
      | main menu | 40  | 23  | U+2500 |
      | main menu | 0   | 12  | U+2502 |
      | main menu | 79  | 12  | U+2502 |
      | map       | 0   | 0   | U+250C |
      | map       | 79  | 23  | U+2518 |
      | map       | 40  | 0   | U+2500 |
      | map       | 0   | 12  | U+2502 |
      | options   | 0   | 0   | U+250C |
      | options   | 79  | 23  | U+2518 |
      | options   | 40  | 23  | U+2500 |
      | options   | 79  | 12  | U+2502 |

  Scenario Outline: The border follows the grid's size
    Given the game is on the main menu screen
    When the screen is rendered into a grid of <cols> columns by <rows> rows
    Then the cell at column <last_col>, row <last_row> holds glyph U+2518 in WINDOW_BORDER on BACKGROUND
    And the cell at column <last_col>, row 0 holds glyph U+2510 in WINDOW_BORDER on BACKGROUND
    And the cell at column 0, row <last_row> holds glyph U+2514 in WINDOW_BORDER on BACKGROUND

    Examples:
      | cols | rows | last_col | last_row |
      | 100  | 30   | 99       | 29       |
      | 120  | 40   | 119      | 39       |
      | 81   | 25   | 80       | 24       |

  Scenario Outline: The border leaves the cells just inside it blank
    Given the game is on the <screen> screen
    When the screen is rendered into a grid of 80 columns by 24 rows
    Then the cell at column 1, row 1 is still blank
    And the cell at column 78, row 22 is still blank

    Examples:
      | screen    |
      | main menu |
      | map       |
      | options   |

  Scenario Outline: Every screen renders into a grid of the requested size
    Given the game is on the <screen> screen
    When the screen is rendered into a grid of 120 columns by 40 rows
    Then the buffer has 120 columns and 40 rows

    Examples:
      | screen    |
      | main menu |
      | map       |
      | options   |

# Non-goals:
#   - Frames, title bars, hint bars, widgets: #11. This file draws a single-line
#     box because the issue asks for the helper, not because anything on
#     screen uses one yet.
#   - Other box styles (heavy, double, rounded) and line joins where two boxes
#     touch: added with the first widget that needs them.
#   - Text wrapping and multi-line text: a widget concern. A write is one
#     line; a newline is not interpreted.
#   - Width-2 glyphs (CJK, emoji), combining marks and right-to-left text: a
#     cell holds one character of the font.
#   - The font as mod content, its size and the resulting cell size. The pure
#     steps take the cell size as a number; the Quil layer reads it from the
#     loaded font. 960x600 stays the default window (game_window.feature); the
#     font size that makes that exactly 80x24 (about 20 px at a 12x25 cell) is
#     tuned in the playtest.
#   - The theme-color wiring of the menu (background 0,0,0, the selected item
#     on 192,192,192, the other items in 255,255,255) is pinned once, in
#     themes.feature, not repeated here.
#   - Which screens use which theme keys beyond the menu's two. The other keys
#     have no drawing scenario until #11 draws borders, tables and scrollbars.
#
# Risks:
#   - The border was added after the first playtest ("its difficult to see the
#     grid"; intent Clarifications). It is a scope addition to the issue, which
#     lists frames as out (#11); #11's frames may replace it. It uses
#     WINDOW_BORDER, not BORDER, because that key is the theme's window frame.
#     On a window smaller than 80x24 cells the grid is clamped and the window
#     clips it, so the right and bottom border are not shown; that is the clamp
#     rule, not a border bug.
#   - The border takes the outermost row and column, and no screen writes there
#     (text rows are 2 to 20, centered between the edges), so the position
#     scenarios above are unchanged. A future screen that writes on row 0 or
#     the last row would overwrite it.
#   - The blit is untested by design. veil.ui.draw stays under bb shell-check,
#     so every decision (whether a cell draws a rectangle, a glyph or nothing,
#     the pixel position, the color) lives in the pure step, and the scenarios
#     above are the only check that the pure step is right. A wrong cell size
#     passed from the shell shows only in the playtest.
#   - "Blank cells add no draw commands" is a performance choice written as
#     behavior: a 160x43 grid is 6,880 cells at 30 frames a second, and
#     drawing every one would cost two Processing calls each. If the coder
#     finds a merged-runs form is needed (one rectangle per run of equal
#     background), that is a deviation from "one rectangle per cell" to raise
#     here, not absorb.
#   - The old draw commands are consumed elsewhere: the steps in
#     acceptance/veil/acceptance/steps/startup.clj and themes.clj and
#     spec/veil/ui/view_spec.clj all read view/frame. They are rewritten with
#     this change; that is why the older feature files are edited.
#   - The row layout is carried over one to one: logical rows 2, 4, 6, 8, 12
#     and 20 become cell rows. Rows were 40 px apart and are now one cell
#     (about 25 px) apart, so the menu is denser than before; how it looks is
#     for the playtest, and the row numbers are a one-line change if it isn't
#     right.
#   - Centering rounds down: an odd number of spare columns puts the extra one
#     on the right, so "Options" (7 wide) in 80 columns starts at 36, not 36.5.
#   - Box-drawing glyphs are named by code point (U+250C, ...), not written out,
#     so the feature file is plain ASCII and can't be misread under a
#     non-UTF-8 default charset on Windows.
#
# Open questions:
#   - None left in this file. The font (which one, its place in a mod's fonts
#     folder, how a mod replaces it, what a missing or bad one does) is settled
#     as mod content in its own concept; see the intent's Clarifications.
