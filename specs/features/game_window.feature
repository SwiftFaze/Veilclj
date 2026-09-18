# mutation-stamp: sha256=581b94c3f935a08a03c15dd7727e2e1817d4aa4dc8860f82c0f1bfe9e2c9eb2b
# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-09-18T19:57:13.495683700Z","feature_name":"Game window","feature_path":"specs/features/game_window.feature","background_hash":"74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b","implementation_hash":"unknown","scenarios":[{"index":1,"name":"The window opens at its default size","scenario_hash":"1671ea5fab89ba4cf0a442b9cc667f6d4a4345fc84b462610216bdc939f5ce84","mutation_count":2,"result":{"Total":2,"Killed":2,"Survived":0,"Errors":0},"tested_at":"2026-09-18T19:30:30.062842800Z"}]}
# acceptance-mutation-manifest-end

Feature: Game window
  The game opens a single window titled Veil at a fixed default size.

  Covers: the window's title and default size.
  Out of scope: fullscreen, resizing, and anything drawn inside the window.

  Scenario: The window is titled Veil
    Given the game is launched
    Then the window title is Veil

  Scenario: The window opens at its default size
    Given the game is launched
    Then the window is <width> by <height> pixels

    Examples:
      | width | height |
      | 960   | 600    |
