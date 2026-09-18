# mutation-stamp: sha256=27e96604a27a6d92405a5a950e29970a268bb05d31273c84c7ee3f06a1ba301d
# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-09-18T20:19:42.546038Z","feature_name":"Main menu","feature_path":"specs/features/main-menu.feature","background_hash":"c278d9acbaf415d175b5b358c977d9702949c29bb8d5724ff7d60e6fd1f179e6","implementation_hash":"unknown","scenarios":[{"index":1,"name":"Moving the selection wraps at both ends","scenario_hash":"399440a71aefc75461aba55a008425bc4bcf4b0d971d91f9bf66358192acdf85","mutation_count":24,"result":{"Total":24,"Killed":24,"Survived":0,"Errors":0},"tested_at":"2026-09-18T20:19:42.546038Z"},{"index":4,"name":"Esc on a screen opened from the menu returns to the menu","scenario_hash":"bd2b5721befb23df3b465d35f90a3ca36c8dcc7d6be27e6b4eb255089b839c74","mutation_count":2,"result":{"Total":2,"Killed":2,"Survived":0,"Errors":0},"tested_at":"2026-09-18T20:19:42.546038Z"},{"index":7,"name":"Keys the menu doesn't use change nothing","scenario_hash":"5ae8a37d4207e53d153c1c15daa1f506369e60f3c3ae62cd64dc17b21170ee40","mutation_count":3,"result":{"Total":3,"Killed":3,"Survived":0,"Errors":0},"tested_at":"2026-09-18T20:19:42.546038Z"}]}
# acceptance-mutation-manifest-end

Feature: Main menu
  The game starts on a main menu the player navigates with the keyboard.
  New Game opens a placeholder map screen, Options opens an empty Options
  screen, Esc returns from either to the menu, and Quit ends the game.

  Covers: the startup screen, menu items and their order, moving the
    selection (arrow keys and W/S, wrapping at both ends), choosing each
    item, and Esc: back to the menu from the map and Options screens (keeping
    the chosen item selected), and ignored on the menu itself.
  Supersedes: nothing.
  Out of scope: any actual Options settings (the screen is an empty stub),
    save/load, world generation, moving the @ (it is static), mouse input,
    and how any screen is drawn (covered by the human playtest, not by these
    steps).

  Background:
    Given the game has just started

  Scenario: The game starts on the main menu with New Game selected
    Then the current screen is the main menu
    And the menu items are New Game, Options, Quit
    And the selected menu item is New Game

  Scenario Outline: Moving the selection wraps at both ends
    Given the selected menu item is <start>
    When the player presses <key>
    Then the selected menu item is <selected>
    And the current screen is the main menu

    Examples:
      | start    | key  | selected |
      | New Game | Down | Options  |
      | Options  | Down | Quit     |
      | Quit     | Down | New Game |
      | New Game | Up   | Quit     |
      | Quit     | Up   | Options  |
      | Options  | Up   | New Game |
      | New Game | S    | Options  |
      | New Game | W    | Quit     |

  Scenario: Choosing New Game opens the map screen with the player on it
    Given the selected menu item is New Game
    When the player presses Enter
    Then the current screen is the map screen
    And the map shows the player as @

  Scenario: Choosing Options opens the empty Options screen
    Given the selected menu item is Options
    When the player presses Enter
    Then the current screen is the options screen

  Scenario Outline: Esc on a screen opened from the menu returns to the menu
    Given the selected menu item is <item>
    And the player presses Enter
    When the player presses Esc
    Then the current screen is the main menu
    And the selected menu item is <item>

    Examples:
      | item     |
      | New Game |
      | Options  |

  Scenario: Esc on the main menu does nothing
    Given the selected menu item is Options
    When the player presses Esc
    Then the current screen is the main menu
    And the selected menu item is Options
    And the game is not over

  Scenario: Choosing Quit ends the game
    Given the selected menu item is Quit
    When the player presses Enter
    Then the game is over

  Scenario Outline: Keys the menu doesn't use change nothing
    Given the selected menu item is Options
    When the player presses <key>
    Then the selected menu item is Options
    And the current screen is the main menu
    And the game is not over

    Examples:
      | key   |
      | X     |
      | Space |
      | Left  |

# Non-goals:
#   - Options settings of any kind; save/load; world generation; mouse input.
#   - Moving the @ - it is static; movement gets its own issue.
#
# Risks:
#   - "the game is over" is a state flag; the window actually closing is Quil
#     wiring in veil.main and is only proven by the playtest.
#   - Key names (Down, S, Esc...) are translated from Quil key events in
#     veil.ui; a wrong translation passes these steps only if the steps use the
#     same translation, so the steps must go through veil.ui's translator.
#
# Open questions: none.
