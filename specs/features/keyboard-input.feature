# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-09-20T09:11:20.631236300Z","feature_name":"Keyboard input and focus-first dispatch","feature_path":"specs/features/keyboard-input.feature","background_hash":"c278d9acbaf415d175b5b358c977d9702949c29bb8d5724ff7d60e6fd1f179e6","implementation_hash":"unknown","scenarios":[{"index":0,"name":"A navigation key becomes its navigation action","scenario_hash":"a36b9680c9a4d88e27f66a80a8ca6a5cd38f505e0968c23ac413beabe5dfddb9","mutation_count":28,"result":{"Total":28,"Killed":28,"Survived":0,"Errors":0},"tested_at":"2026-09-20T09:03:25.535565400Z"},{"index":5,"name":"A key with no translation becomes no input at all","scenario_hash":"e10d768a4e2ab340f83a79729fcf2df9503e9daa983942471aeae1e89f173079","mutation_count":3,"result":{"Total":3,"Killed":3,"Survived":0,"Errors":0},"tested_at":"2026-09-20T09:03:25.535565400Z"},{"index":9,"name":"The earliest consuming handler in the chain decides the outcome","scenario_hash":"1ecb0417dcd0ba4ee93966fe7c0de2185e64d51ca2dde345aefaaf2ee8ea8819","mutation_count":6,"result":{"Total":6,"Killed":6,"Survived":0,"Errors":0},"tested_at":"2026-09-20T09:03:25.535565400Z"},{"index":10,"name":"A handler that does not consume leaves the decision to the next one","scenario_hash":"8732b54b429cc532fc3c98abc7a2de26a1bf5dac7048a5703de483ac2ce05493","mutation_count":3,"result":{"Total":3,"Killed":3,"Survived":0,"Errors":0},"tested_at":"2026-09-20T09:03:25.535565400Z"},{"index":12,"name":"The existing screens keep their behaviour when nothing consumes the input","scenario_hash":"a5b6a96c87d22f146525350cfc6d70fca40b238cd3fcccaea264ce24d1831c3f","mutation_count":9,"result":{"Total":9,"Killed":9,"Survived":0,"Errors":0},"tested_at":"2026-09-20T09:03:25.535565400Z"},{"index":14,"name":"Esc still returns to the menu from a screen opened from it","scenario_hash":"def24715cd4b7dbb0ac1a01fe3ad13605db1a8e9e7b042bd340178e497d4b6b7","mutation_count":2,"result":{"Total":2,"Killed":2,"Survived":0,"Errors":0},"tested_at":"2026-09-20T09:03:25.535565400Z"},{"index":15,"name":"Inputs a screen does not use change nothing","scenario_hash":"7beb98a09e11dbf366f92a59ec51ca11dc5a12bdfb9a33efef2315746339e124","mutation_count":6,"result":{"Total":6,"Killed":6,"Survived":0,"Errors":0},"tested_at":"2026-09-20T09:03:25.535565400Z"}]}
# acceptance-mutation-manifest-end

Feature: Keyboard input and focus-first dispatch
  A key press becomes one of three things: a navigation action named by a
  keyword, a printable character, or a character with modifiers held. The
  translation is mechanical - a key becomes what it literally is - so a
  focused text field receives a raw "w" rather than "move up".

  An input is then offered to each handler in a focus-first chain: the topmost
  overlay, then the focused pane, then the focused widget. The first handler
  that consumes it decides the outcome, and the input never reaches anything
  behind it. Only an input nothing consumed reaches the screen-level bindings,
  which is where the W/S aliases and the accelerators live.

  Covers: what each key translates to (navigation actions, printable
    characters, Ctrl chords, Shift+Tab, and keys with no translation); Esc
    meaning back rather than quit; the focus-first chain (the earliest
    consumer winning, an unconsumed input falling through to the screen
    bindings, and an empty chain behaving as the screen bindings alone); and
    the existing three screens keeping their behaviour through the chain,
    including W and S moving the menu selection as screen-level aliases rather
    than as something the translator knows. Translation preserves the case the
    player typed, so the menu's W/S aliases accept either case - which is what
    keeps main-menu.feature's uppercase W and S examples passing.
  Supersedes: the key-to-input vocabulary wherever another feature file
    pinned it. deterministic-keyboard-qa.feature's "A script key reaches the
    game as the same input a real key press gives" table asserted the old
    four-input vocabulary (W and S as movement, X and Space and Left as no
    input at all); that table now follows this file, while the property it is
    named for - scripted and typed keys agreeing - stays its own.
    main-menu.feature's scenarios all keep passing untouched and it remains
    the owner of what each screen does with an input. The one thing overturned
    for the player is nothing: W and S are no longer translated to :up and
    :down, they are characters the main menu interprets on fall-through, so
    the observable result of pressing W is unchanged.
  Out of scope: key rebinding and mouse input (outside this milestone); the
    frame clock, which is the same issue but a separate concept
    (frame-clock.feature); any actual overlay, pane or widget - issues #11-#16
    add those and this file only specifies the chain they plug into, so every
    handler here is one a scenario supplies; widening the QA event vocabulary;
    how anything is drawn (terminal-cell-grid.feature); and the two pieces of
    shell wiring that no scenario can reach, namely the shell reading Quil's
    modifier set into the event map and zeroing Processing's key field on Esc.
    Both need a live window and belong to the human playtest and bb qa.
  QA: specs/qa/keyboard-input.keys and .edn drive arrows, W/S, Enter and Esc
    through the real key path as a regression check that the existing screens
    behave identically after the rewrite. It covers the old behaviour only:
    every input this feature newly adds causes no state change on the three
    current screens, and an event is only logged when the state changed.

  Background:
    Given the game has just started

  # --- What a key becomes ---

  Scenario Outline: A navigation key becomes its navigation action
    When the player presses <key>
    Then the input is the <action> action

    Examples:
      | key       | action    |
      | Up        | up        |
      | Down      | down      |
      | Left      | left      |
      | Right     | right     |
      | Enter     | confirm   |
      | Esc       | back      |
      | Tab       | tab       |
      | Space     | toggle    |
      | Backspace | backspace |
      | Delete    | delete    |
      | Home      | home      |
      | End       | end       |
      | Page Up   | page-up   |
      | Page Down | page-down |

  Scenario Outline: A printable key becomes that character
    When the player types <char>
    Then the input is the character <char>

    Examples:
      | char |
      | a    |
      | w    |
      | s    |
      | Z    |
      | 7    |
      | ?    |

  Scenario: W is a character, not a movement
    When the player types w
    Then the input is the character w
    And the input is not the up action

  Scenario Outline: A letter with Ctrl held becomes a chord
    When the player types <char> with Ctrl held
    Then the input is the character <char> with the ctrl modifier

    Examples:
      | char |
      | a    |
      | b    |
      | q    |

  Scenario: Tab with Shift held is a different action from Tab
    When the player presses Tab with Shift held
    Then the input is the shift-tab action

  Scenario Outline: A key with no translation becomes no input at all
    When the player presses <key>
    Then there is no input

    Examples:
      | key      |
      | F1       |
      | Caps Lock|
      | nothing  |

  Scenario: Esc means back and never ends the game
    Given an empty chain
    And the selected menu item is Options
    When the player presses Esc
    Then the input is the back action
    And the game is not over
    And the current screen is the main menu

  # --- The focus-first chain ---

  Scenario: A handler that consumes an input stops it reaching the screen bindings
    Given a chain whose overlay handler consumes every input and opens the map screen
    And the selected menu item is New Game
    When the down action is dispatched
    Then the current screen is the map screen
    And the selected menu item is New Game

  Scenario: An input no handler consumed reaches the screen bindings
    Given a chain of an overlay handler, a pane handler and a widget handler
    And no handler consumes anything
    And the selected menu item is New Game
    When the down action is dispatched
    Then the selected menu item is Options
    And the current screen is the main menu

  Scenario Outline: The earliest consuming handler in the chain decides the outcome
    Given a chain of an overlay handler, a pane handler and a widget handler
    And the <first> handler consumes every input and opens the map screen
    And the <second> handler consumes every input and opens the options screen
    When the down action is dispatched
    Then the current screen is the map screen

    Examples:
      | first   | second |
      | overlay | pane   |
      | overlay | widget |
      | pane    | widget |

  Scenario Outline: A handler that does not consume leaves the decision to the next one
    Given a chain of an overlay handler, a pane handler and a widget handler
    And only the <consumer> handler consumes, opening the map screen
    When the down action is dispatched
    Then the current screen is the map screen

    Examples:
      | consumer |
      | overlay  |
      | pane     |
      | widget   |

  Scenario Outline: An empty chain behaves exactly as the screen bindings alone
    Given an empty chain
    And the selected menu item is <start>
    When the <action> action is dispatched
    Then the selected menu item is <selected>

    Examples:
      | start    | action | selected |
      | New Game | up     | Quit     |
      | New Game | down   | Options  |
      | Quit     | down   | New Game |

  # --- The existing screens, through the chain ---

  Scenario Outline: The existing screens keep their behaviour when nothing consumes the input
    Given an empty chain
    And the selected menu item is <start>
    When the player presses <key>
    Then the selected menu item is <selected>
    And the current screen is the main menu

    Examples:
      | start    | key  | selected |
      | New Game | Down | Options  |
      | Quit     | Down | New Game |
      | New Game | Up   | Quit     |

  Scenario Outline: Choosing a menu item still works through the chain
    Given an empty chain
    And the selected menu item is <item>
    When the player presses Enter
    Then <outcome>

    Examples:
      | item     | outcome                         |
      | New Game | the current screen is the map screen     |
      | Options  | the current screen is the options screen |
      | Quit     | the game is over                |

  Scenario Outline: Esc still returns to the menu from a screen opened from it
    Given an empty chain
    And the selected menu item is <item>
    And the player presses Enter
    When the player presses Esc
    Then the current screen is the main menu
    And the selected menu item is <item>

    Examples:
      | item     |
      | New Game |
      | Options  |

  Scenario Outline: Inputs a screen does not use change nothing
    Given an empty chain
    And the selected menu item is Options
    When the player presses <key>
    Then the selected menu item is Options
    And the current screen is the main menu
    And the game is not over

    Examples:
      | key       |
      | Left      |
      | Right     |
      | Tab       |
      | Home      |
      | Page Down |
      | Backspace |

  Scenario Outline: W and S still move the menu selection, as screen-level aliases
    Given an empty chain
    And the selected menu item is <start>
    When the player types <char>
    Then the selected menu item is <selected>

    Examples:
      | start    | char | selected |
      | New Game | s    | Options  |
      | New Game | w    | Quit     |
      | Quit     | s    | New Game |
      | New Game | S    | Options  |
      | New Game | W    | Quit     |

  Scenario: A character a widget consumed never reaches the menu's W alias
    Given a chain whose widget handler consumes every character
    And the selected menu item is New Game
    When the player types w
    Then the selected menu item is New Game
    And the current screen is the main menu

  Scenario: A navigation action still reaches the menu when only characters are consumed
    Given a chain whose widget handler consumes every character
    And the selected menu item is New Game
    When the player presses Down
    Then the selected menu item is Options

# Non-goals:
#   - Key rebinding and mouse input; both are outside this milestone.
#   - Any real overlay, pane or widget. Every handler in this file is supplied
#     by the scenario; issues #11-#16 add the real ones.
#   - Reading the frame clock (frame-clock.feature) and drawing anything
#     (terminal-cell-grid.feature).
#
# Risks:
#   - Two pieces of shell wiring are unreachable from any scenario here: the
#     shell reading Quil's modifier set into the event map, and the shell
#     zeroing Processing's key field so Esc does not quit. The steps supply
#     the modifier field directly and assert on the state, so "with Ctrl held"
#     and "never ends the game" pass even if the shell does neither. Both are
#     the human playtest's and bb qa's to prove; this is the same class of gap
#     as the existing Esc/Processing workaround.
#   - W and S moving from the translator to the screen bindings is a
#     behaviour-preserving change with no observable difference, so no scenario
#     can catch the case where both the translator and the menu do it. "W is a
#     character, not a movement" and "A character a widget consumed never
#     reaches the menu's W alias" are together what pin it.
#   - A handler that does not consume cannot change the state, so no scenario
#     can observe that such a handler was offered the input at all. Every
#     dispatch scenario here therefore asserts on the outcome rather than on
#     who saw what. If a future contract lets a non-consuming handler record
#     something, that becomes observable and gets its own scenarios.
#
# Open questions:
#   - The handler contract (a handler returns the new state, or nil to bubble)
#     was assumed, not decided: see specs/intent/keyboard-input.md. If the
#     reviewer prefers an explicit {:state :consumed?} result, every scenario
#     under "The focus-first chain" keeps its wording and only the step
#     handlers change.
#   - The chain is an argument rather than a structure in the state, so this
#     file specifies no focus fields for issue #11 to fill. Also an assumption.
