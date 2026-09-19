# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-09-19T18:32:17.053088200Z","feature_name":"Thin Quil shell","feature_path":"specs/features/thin-quil-shell.feature","background_hash":"74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b","implementation_hash":"unknown","scenarios":[{"index":7,"name":"A draw command carries the colour its selection state calls for","scenario_hash":"e44b668d68fae71a180a20899bd366b0e8363d98cf18cb34dc61a5f2f16391d0","mutation_count":6,"result":{"Total":6,"Killed":6,"Survived":0,"Errors":0},"tested_at":"2026-09-19T18:31:35.758465400Z"},{"index":6,"name":"A raw Escape is recognised so Processing's quit can be suppressed","scenario_hash":"18f13df2f0b680fb26d2a0940817aec874281acf785706de505ce4ba88cd254d","mutation_count":8,"result":{"Total":8,"Killed":8,"Survived":0,"Errors":0},"tested_at":"2026-09-19T15:35:25.110079300Z"},{"index":8,"name":"A draw command carries the pixel position for its row","scenario_hash":"2066fbcc77e2e6e155a5c31914b99a253be787ad6f7687d04160245f95c68dd0","mutation_count":16,"result":{"Total":16,"Killed":16,"Survived":0,"Errors":0},"tested_at":"2026-09-19T15:35:25.110079300Z"},{"index":9,"name":"Every draw command on every screen carries a colour and a position","scenario_hash":"d1d4acde51fd0d68ad7f041fbb3146d0309d6fd7b2852acdaeb7f0710c8452f8","mutation_count":3,"result":{"Total":3,"Killed":3,"Survived":0,"Errors":0},"tested_at":"2026-09-19T15:35:25.110079300Z"},{"index":12,"name":"Arithmetic and comparison are findings wherever they appear","scenario_hash":"179e2d1088649524021be0d6a74c9f78045777bb7bcfb64c6ff5fa0712a62703","mutation_count":15,"result":{"Total":15,"Killed":15,"Survived":0,"Errors":0},"tested_at":"2026-09-19T15:35:25.110079300Z"},{"index":17,"name":"A shell file that is missing is a finding, so a rename can't switch the check off","scenario_hash":"1f46bef12cdeab577f7731361fe7d8ba26f7c89064b629d9c0b3eb990cd9fe14","mutation_count":4,"result":{"Total":4,"Killed":4,"Survived":0,"Errors":0},"tested_at":"2026-09-19T15:35:25.110079300Z"}]}
# acceptance-mutation-manifest-end

Feature: Thin Quil shell
  Everything the game decides at startup, on a key and about where and in what
  colour a line of text is drawn is a pure function with specs. What is left in
  veil.main and veil.ui.draw is Quil and Processing calls that decide nothing,
  so the only uncovered lines are the ones a window is needed to run, and a
  gate check fails the build if a decision or a calculation creeps back in.

  Covers: the mod-load outcome at startup (a clean load gives the registry; a
    failed load gives the error report with every problem, and exit status 1),
    what a launch problem (bad arguments, failed mod load) stops the game with,
    the starting state (the initial state with the mods registry attached),
    recognising the raw Escape key that Processing would otherwise treat as
    quit, the colour and pixel position carried by every draw command, and the
    shell check: which guards, calculations and branching forms it accepts and
    which it reports in veil.main and veil.ui.draw, how findings are reported,
    and that a missing shell file is itself a finding.
  Supersedes: nothing, except that its colour examples now pin core:default's
    theme colours instead of the old hard-coded ones (themes.feature). It narrows one out-of-scope item in
    deterministic-keyboard-qa.feature: that file leaves "quitting the process
    when the script ends" to the playtest, but the decision to quit is already
    specced (mode/frame's :exit?), so only the q/exit call is left uncovered.
  Out of scope: the Quil and Processing calls themselves (q/sketch, q/text,
    q/exit, and the set! on the applet's key field that suppresses Processing's
    quit): they need a live applet, and the scripted QA run and the human
    playtest cover them. Also reading the mods directory from disk
    (veil.mods.disk, specced against temp dirs); what a mod-load error says
    (mod-loader.feature) or a bad launch argument says
    (deterministic-keyboard-qa.feature); translating a key to a game input and
    what the game does with it (main-menu.feature); and any change in
    behaviour: the game plays exactly as before.
  QA: none - a refactor with no new keyboard behavior; specs/qa/main-menu.keys already drives the paths it moves.

  # --- Startup: the mod-load outcome ---

  Scenario: A clean mod load gives the registry to start with
    Given the mods directory holds mod "core" with no dependencies
    When the mods are loaded for startup
    Then startup has the registry
    And startup has no error

  Scenario: A failed mod load gives the error report and exit status 1
    Given the mods directory holds mod "broken-pack" depending on "nonexistent-mod"
    When the mods are loaded for startup
    Then startup has no registry
    And the startup error starts with "Failed to load mods:"
    And the startup error names "broken-pack" and "nonexistent-mod"
    And the startup exit status is 1

  Scenario: A failed mod load reports every problem the loader found
    Given the mods directory holds mod "core" with widget files "core/widgets/lever.json" and "core/widgets/crank.json" that are both missing their label
    When the mods are loaded for startup
    Then the startup error names "core/widgets/lever.json"
    And the startup error names "core/widgets/crank.json"

  # --- Startup: what stops a launch ---

  Scenario Outline: A launch problem stops the game with its message and exit status 1
    Given the mods directory holds <mods>
    When the game is started with arguments "<args>"
    Then the game does not start
    And the launch message starts with "<message>"
    And the launch exit status is 1

    Examples:
      | mods                                             | args    | message                  |
      | mod "core" with no dependencies                  | --bogus | unknown argument --bogus |
      | mod "core" with no dependencies                  | --keys  | --keys needs a file path |
      | mod "broken-pack" depending on "nonexistent-mod" |         | Failed to load mods:     |

  Scenario: A launch with no problem starts the game with the mods registry
    Given the mods directory holds mod "core" with no dependencies
    When the game is started with arguments ""
    Then the game starts
    And the game starts with the mods registry

  # --- The starting state ---

  Scenario: The starting state is the initial game state with the registry attached
    Given a mods registry
    When the starting state is built
    Then the starting screen is the main menu
    And the selected menu item is New Game
    And the game is not over
    And the starting state carries that registry

  # --- Keys ---

  Scenario Outline: A raw Escape is recognised so Processing's quit can be suppressed
    When a key event arrives with raw key <raw key>
    Then the event <verdict> a raw Escape

    Examples:
      | raw key    | verdict |
      | Escape     | is      |
      | Enter      | is not  |
      | a          | is not  |
      | no raw key | is not  |

  # --- Draw commands ---

  Scenario Outline: A draw command carries the colour its selection state calls for
    Given the main menu with <item> selected
    When the draw commands are built for a window 960 pixels wide
    Then the command for <item> has the colour 192 192 192
    And the command for <other> has the colour 255 255 255
    And the command for "VEIL" has the colour 255 255 255

    Examples:
      | item     | other    |
      | New Game | Options  |
      | Options  | Quit     |
      | Quit     | New Game |

  Scenario Outline: A draw command carries the pixel position for its row
    Given the main menu with New Game selected
    When the draw commands are built for a window <width> pixels wide
    Then the command on row <row> is drawn at x <x> and y <y>

    Examples:
      | width | row | x   | y   |
      | 960   | 2   | 480 | 130 |
      | 960   | 4   | 480 | 210 |
      | 960   | 12  | 480 | 530 |
      | 800   | 2   | 400 | 130 |

  Scenario Outline: Every draw command on every screen carries a colour and a position
    Given the game is on the <screen> screen
    When the draw commands are built for a window 960 pixels wide
    Then there is at least one command
    And every command has a colour, an x and a y

    Examples:
      | screen    |
      | main menu |
      | map       |
      | options   |

  # --- The shell check: what a shell may contain ---

  Scenario Outline: A shell may guard on one already-computed value
    Given src/veil/main.clj contains the form <form>
    When the shell check runs
    Then there are no findings
    And the shell check exits with status 0

    Examples:
      | form                                                     |
      | (when exit? (q/exit))                                    |
      | (if (:error launched) (die launched) (open-window))      |
      | (when (input/escape? event) (q/exit))                    |
      | (q/text text x y)                                        |

  Scenario Outline: A guard on an expression is a finding
    Given src/veil/main.clj contains the form <form>
    When the shell check runs
    Then the only finding is src/veil/main.clj line 2: a guard on an expression
    And the shell check exits with status 1

    Examples:
      | form                                                    |
      | (when (= (char 27) (:raw-key event)) (q/exit))          |
      | (when (not exit?) (q/exit))                             |
      | (if (and ready? exit?) (q/exit) nil)                    |
      | (when (input/escape? (first events)) (q/exit))          |
      | (when (q/key-pressed?) (q/exit))                        |

  Scenario Outline: Arithmetic and comparison are findings wherever they appear
    Given src/veil/ui/draw.clj contains the form (q/text text x (<operator> y 1))
    When the shell check runs
    Then the only finding is src/veil/ui/draw.clj line 2: calculation with <operator>
    And the shell check exits with status 1

    Examples:
      | operator |
      | +        |
      | -        |
      | *        |
      | /        |
      | inc      |
      | dec      |
      | mod      |
      | quot     |
      | rem      |
      | =        |
      | not=     |
      | <        |
      | >        |
      | <=       |
      | >=       |

  Scenario Outline: A multi-way branch is a finding
    Given src/veil/main.clj contains the form <form>
    When the shell check runs
    Then the only finding is src/veil/main.clj line 2: a multi-way branch
    And the shell check exits with status 1

    Examples:
      | form                                         |
      | (cond a (q/exit) :else (q/frame-rate 30))    |
      | (case mode :a (q/exit) (q/frame-rate 30))    |
      | (condp = mode :a (q/exit))                   |

  # --- The shell check: reporting ---

  Scenario: Every finding names its file and line, and all are reported together
    Given src/veil/ui/draw.clj has the lines "(q/text t (/ (q/width) 2) 0)" and "(when (= state 1) (q/exit))" after its ns form
    When the shell check runs
    Then the findings are src/veil/ui/draw.clj line 2: calculation with /, src/veil/ui/draw.clj line 3: a guard on an expression
    And the shell check exits with status 1

  Scenario: Only the two shell files are checked
    Given src/veil/ui/view.clj contains the form (+ 50 (* row 40))
    And src/veil/main.clj contains the form (q/exit)
    And src/veil/ui/draw.clj contains the form (q/exit)
    When the shell check runs
    Then there are no findings
    And the shell check exits with status 0

  Scenario: Comments, strings and docstrings are not code
    Given src/veil/main.clj contains the text ";; (when (= a b) (q/exit))" as a comment
    And src/veil/ui/draw.clj contains the text "(+ 1 2) then (cond a b)" as a docstring
    When the shell check runs
    Then there are no findings

  Scenario Outline: A shell file that is missing is a finding, so a rename can't switch the check off
    Given <present> is present and <absent> is absent
    When the shell check runs
    Then the only finding is <absent>: file not found
    And the shell check exits with status 1

    Examples:
      | present               | absent                |
      | src/veil/main.clj     | src/veil/ui/draw.clj  |
      | src/veil/ui/draw.clj  | src/veil/main.clj     |

# Non-goals:
#   - Any behaviour change. Every scenario here describes what the game
#     already does; the point is where the code that does it lives.
#   - Testing Quil or Processing. q/sketch, q/text, q/exit and the applet's
#     key field stay uncovered, and that is the rule this feature establishes,
#     not a gap it closes.
#   - prevent-processing-exit's set!. Only the "is this Escape?" question
#     moves out; the write to the applet needs a live window (#24's key
#     scripts drive it, and an Esc-doesn't-quit script belongs there).
#   - A veil.main coverage exception in the gates. The fix is to empty the
#     shell, not to exclude it.
#   - A new component or a dependency-checker.edn change: the startup decision
#     is split so each half stays where it already can be (intent, Q1).
#   - The quit check: mode/frame already decides it and is specced.
#   - Checking veil.ui.qa.runner. It is the other Quil-adjacent shell, but it
#     spawns a process and prints, and is out of the issue's scope.
#
# Risks:
#   - High-risk path. The shell check is a new blocking section of
#     check-clean.sh, which is a change to a quality gate's scope
#     (.claude/workflow.md), so the spec waits for approval before any code.
#   - Most of the startup, key and draw scenarios are thin because the change
#     is structural. The real verification is the coverage and mutation
#     numbers for the moved code, and the shell check passing against the real
#     files. If the logic were moved back into veil.main, most of these
#     scenarios would still pass; only the shell check would catch it.
#   - The startup and draw steps must call the extracted functions, not
#     veil.main. A step that reaches into veil.main proves nothing about where
#     the decision lives.
#   - The shell check reads source with the Clojure reader, not text matching,
#     so comments and docstrings are ignored (its own scenario). Resolving an
#     alias such as input/escape? to a veil.* namespace needs the file's ns
#     form, so the steps supply one; a shell file that requires a namespace
#     without an alias is not covered by any scenario.
#   - The guard rule is an interpretation of "a single already-computed, named
#     value" (intent, Clarifications Q6): a symbol, a keyword lookup of one
#     symbol, or a call to a veil.* function with such arguments. It is
#     deliberately strict; loosening it later is a gate-scope change too.
#   - The draw x and y examples hard-code the current layout (x is half the
#     width, y is 50 plus 40 per row). They pin the arithmetic that moves out
#     of draw!, so a deliberate layout change has to edit the table. That is
#     the point, but it is the table most likely to look like churn later.
#   - The colour examples pin core:default's SELECTED_HIGHLIGHT and NORMAL_TEXT (themes.feature
#     supersedes the old hard-coded 255,255,100 and 220,220,220). The unconditional
#     (q/fill 220) before the loop in draw! is dead once each command sets its
#     own colour; removing it is part of the move, not a behaviour change.
#   - The startup outcome is checked in two layers: the mods layer decides the
#     outcome, and the launch handling turns any launch error into a message
#     and exit status 1. The steps need one entry point for "the game is
#     started with arguments", which does not exist yet; the coder defines it.
#
# Open questions: none. All four were settled in specs/intent/thin-quil-shell.md's
#   Clarifications. One reading is recorded there for the reviewer to confirm
#   at approval: the exact set of guard tests the shell check accepts (Q6).
