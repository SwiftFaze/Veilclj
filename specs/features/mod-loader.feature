Feature: Mod loader
  At startup the game discovers every mod under mods/, orders the mods by
  their dependencies, validates every manifest and content file, and builds
  one immutable registry, kept in the state map, that records the load order
  and the content for each content type. Core loads through the same path as
  any third-party mod.

  Covers: manifest discovery (mods/<id>/mod.json with "id" and "dependsOn",
    the folder name matching the id), dependency ordering (core first, then
    any mod whose dependencies have loaded, alphabetical by id among mods
    ready at the same time), unresolved and cyclic dependency errors, the
    namespaced content-ID pattern, a mod's own namespace, collisions (across
    mods and inside one mod) and "overrides" (value must equal the id, the
    target must exist, and its mod must be a direct or transitive
    dependency), the content-type registry (every registered type is walked
    for every mod, in load order), field-level validation diagnostics (file,
    offending path, what was expected) reported all together, the rejection
    of unknown fields, and the empty registry when mods/ is missing.
    The machinery is exercised with fixture content types ("widget" and
    "gadget") defined by the steps, not with real game content.
  Supersedes: nothing. This ports the machinery parts of Java Veil's
    mod-loader.feature and mod-json-contract.feature.
  Deliberately ignored, not errors: folders inside a mod that no registered
    content type claims (Java Veil's tiles/, buildings/... until their types
    land), and files in a claimed folder that aren't *.json.
  Out of scope: every concrete content type, themes included (#8); building
    blueprints and tile references; stats.json; the startup wiring in veil.main
    and how a load error is shown to the player (covered by the human playtest);
    docs/mod-format.md (Step 7 documentation, not observable behavior).

  # --- Discovery and ordering ---

  Scenario: A missing mods directory yields an empty registry
    Given there is no mods directory
    When the mods are loaded
    Then the load order is empty
    And no content of any type is registered

  Scenario: Core loads first and each mod loads after its dependencies
    Given mod "orc-pack" depends on "goblin-pack"
    And mod "goblin-pack" depends on "core"
    And mod "core" has no dependencies
    When the mods are loaded
    Then the load order is core, goblin-pack, orc-pack

  Scenario: Mods ready at the same time load alphabetically by id
    Given mod "core" has no dependencies
    And mod "zeta-pack" depends on "core"
    And mod "elf-pack" depends on "core"
    And mod "goblin-pack" depends on "core"
    When the mods are loaded
    Then the load order is core, elf-pack, goblin-pack, zeta-pack

  Scenario: Mods load without core when none of them need it
    Given mod "goblin-pack" has no dependencies
    And mod "elf-pack" has no dependencies
    When the mods are loaded
    Then the load order is elf-pack, goblin-pack

  Scenario: A dependency on a mod that isn't present fails the load
    Given mod "core" has no dependencies
    And mod "broken-pack" depends on "nonexistent-mod"
    When the mods are loaded
    Then loading fails naming mod "broken-pack" and the unresolved dependency "nonexistent-mod"

  Scenario: A mod that needs core fails when core is missing
    Given mod "goblin-pack" depends on "core"
    When the mods are loaded
    Then loading fails naming mod "goblin-pack" and the unresolved dependency "core"

  Scenario: A dependency cycle fails the load naming every mod in it
    Given mod "core" has no dependencies
    And mod "alpha" depends on "beta"
    And mod "beta" depends on "alpha"
    When the mods are loaded
    Then loading fails naming the cyclic mods "alpha" and "beta"

  Scenario: A mods folder without a mod.json fails the load
    Given mod "core" has no dependencies
    And the mods directory has a folder "stray" with no mod.json
    When the mods are loaded
    Then loading fails naming the folder "stray" as missing its mod.json

  Scenario: A manifest id that differs from its folder name fails the load
    Given mod "core" has no dependencies
    And the mods directory has a folder "goblins" whose manifest id is "goblin-pack"
    When the mods are loaded
    Then loading fails naming the file "goblins/mod.json", the id "goblin-pack" and the folder name "goblins"

  # --- Content registry ---

  Scenario: Content from every mod is registered under its namespaced ID
    Given mod "core" has no dependencies
    And mod "goblin-pack" depends on "core"
    And mod "core" has a widget "core:lever"
    And mod "goblin-pack" has a widget "goblin-pack:totem"
    When the mods are loaded
    Then widget "core:lever" is registered from mod "core"
    And widget "goblin-pack:totem" is registered from mod "goblin-pack"

  Scenario: Every registered content type is loaded for every mod
    Given mod "core" has no dependencies
    And mod "core" has a widget "core:lever"
    And mod "core" has a gadget "core:gear"
    When the mods are loaded
    Then widget "core:lever" is registered from mod "core"
    And gadget "core:gear" is registered from mod "core"
    And no gadget is registered as "core:lever"

  Scenario Outline: A content ID must match the namespace:name pattern
    Given mod "core" has no dependencies
    And mod "core" has a widget "<id>"
    When the mods are loaded
    Then <outcome>

    Examples:
      | id            | outcome                                                                  |
      | core:lever    | widget "core:lever" is registered from mod "core"                        |
      | core:lever_2  | widget "core:lever_2" is registered from mod "core"                      |
      | core:9-lives  | widget "core:9-lives" is registered from mod "core"                      |
      | lever         | loading fails naming the widget file and the malformed id "lever"        |
      | Core:lever    | loading fails naming the widget file and the malformed id "Core:lever"   |
      | core:_lever   | loading fails naming the widget file and the malformed id "core:_lever"  |
      | core:         | loading fails naming the widget file and the malformed id "core:"        |
      | core:a:b      | loading fails naming the widget file and the malformed id "core:a:b"     |

  Scenario: A new ID in another mod's namespace fails the load
    Given mod "core" has no dependencies
    And mod "goblin-pack" depends on "core"
    And mod "goblin-pack" has a widget "core:spear"
    When the mods are loaded
    Then loading fails naming the widget file, the id "core:spear" and that mod "goblin-pack" may only add IDs in namespace "goblin-pack"

  Scenario: A duplicate ID without "overrides" fails naming both mods
    Given mod "core" has no dependencies
    And mod "retexture-pack" depends on "core"
    And mod "core" has a widget "core:lever"
    And mod "retexture-pack" has a widget "core:lever"
    When the mods are loaded
    Then loading fails naming the colliding id "core:lever" and the mods "core" and "retexture-pack"

  Scenario: A duplicate ID inside one mod fails naming both files
    Given mod "core" has no dependencies
    And mod "core" has a widget file "core/widgets/lever.json" containing {"id": "core:lever", "label": "L"}
    And mod "core" has a widget file "core/widgets/lever-old.json" containing {"id": "core:lever", "label": "Old", "overrides": "core:lever"}
    When the mods are loaded
    Then loading fails naming the colliding id "core:lever" and the files "core/widgets/lever.json" and "core/widgets/lever-old.json"

  Scenario: A later mod's "overrides" replaces the earlier entry
    Given mod "core" has no dependencies
    And mod "retexture-pack" depends on "core"
    And mod "core" has a widget "core:lever" labelled "Plain lever"
    And mod "retexture-pack" has a widget "core:lever" labelled "Fancy lever" that overrides "core:lever"
    When the mods are loaded
    Then widget "core:lever" is registered from mod "retexture-pack"
    And widget "core:lever" is labelled "Fancy lever"

  Scenario: An "overrides" value that differs from the file's id fails the load
    Given mod "core" has no dependencies
    And mod "retexture-pack" depends on "core"
    And mod "core" has a widget "core:lever"
    And mod "retexture-pack" has a widget "core:lever" that overrides "core:handle"
    When the mods are loaded
    Then loading fails naming the widget file, the id "core:lever" and the mismatched overrides "core:handle"

  Scenario: Overriding an ID that nothing registered fails the load
    Given mod "core" has no dependencies
    And mod "retexture-pack" depends on "core"
    And mod "retexture-pack" has a widget "core:lever" that overrides "core:lever"
    When the mods are loaded
    Then loading fails naming the widget file and that there is no "core:lever" to override

  Scenario: Overriding without depending on the target's mod fails the load
    Given mod "zzz-pack" has no dependencies
    And mod "aaa-pack" has no dependencies
    And mod "zzz-pack" has a widget "zzz-pack:lever"
    And mod "aaa-pack" has a widget "zzz-pack:lever" that overrides "zzz-pack:lever"
    When the mods are loaded
    Then loading fails naming mod "aaa-pack", the id "zzz-pack:lever" and that it must declare "dependsOn" of "zzz-pack"

  Scenario: Overriding through a transitive dependency works
    Given mod "core" has no dependencies
    And mod "goblin-pack" depends on "core"
    And mod "orc-pack" depends on "goblin-pack"
    And mod "core" has a widget "core:lever" labelled "Plain lever"
    And mod "orc-pack" has a widget "core:lever" labelled "Orcish lever" that overrides "core:lever"
    When the mods are loaded
    Then widget "core:lever" is registered from mod "orc-pack"
    And widget "core:lever" is labelled "Orcish lever"

  # --- Validation ---

  Scenario Outline: A malformed content file fails with a field-level diagnostic
    Given mod "core" has no dependencies
    And mod "core" has a widget file "core/widgets/lever.json" containing <json>
    When the mods are loaded
    Then the load errors include the file "core/widgets/lever.json", the path "<path>" and that it expected <expected>

    Examples:
      | json                                                                  | path             | expected         |
      | {"id": "core:lever"}                                                  | /label           | a required field |
      | {"id": "core:lever", "label": 7}                                      | /label           | a string         |
      | {"id": "core:lever", "label": "L", "colors": {"BORDER": {"r": "x"}}}  | /colors/BORDER/r | an integer       |
      | {"id": "core:lever", "label": "L", "lable": "typo"}                   | /lable           | no such field    |

  Scenario Outline: A malformed manifest fails with a field-level diagnostic
    Given mod "core" has a manifest "core/mod.json" containing <json>
    When the mods are loaded
    Then the load errors include the file "core/mod.json", the path "<path>" and that it expected <expected>

    Examples:
      | json                                     | path         | expected          |
      | {"dependsOn": []}                        | /id          | a required field  |
      | {"id": "core", "dependsOn": "goblins"}   | /dependsOn   | a list of mod ids |
      | {"id": "core", "dependsOn": [3]}         | /dependsOn/0 | a string          |
      | {"id": "core", "version": "1.0"}         | /version     | no such field     |

  Scenario: Every validation problem is reported together
    Given mod "core" has no dependencies
    And mod "goblin-pack" has a manifest "goblin-pack/mod.json" containing {"id": "goblin-pack", "version": "1.0"}
    And mod "core" has a widget file "core/widgets/lever.json" containing {"id": "core:lever", "label": 7}
    And mod "core" has a widget file "core/widgets/crank.json" containing {"id": "core:crank"}
    When the mods are loaded
    Then the load errors include the file "goblin-pack/mod.json", the path "/version" and that it expected no such field
    And the load errors include the file "core/widgets/lever.json", the path "/label" and that it expected a string
    And the load errors include the file "core/widgets/crank.json", the path "/label" and that it expected a required field

  Scenario: A file that isn't valid JSON fails naming the file
    Given mod "core" has no dependencies
    And mod "core" has a widget file "core/widgets/lever.json" containing { not valid json
    When the mods are loaded
    Then the load errors include the file "core/widgets/lever.json" as not valid JSON

  Scenario: Folders and files the loader doesn't claim are ignored
    Given mod "core" has no dependencies
    And mod "core" has a widget "core:lever"
    And mod "core" has a file "core/tiles/grass.json" containing { not valid json
    And mod "core" has a file "core/widgets/README.md" containing not json at all
    When the mods are loaded
    Then the load order is core
    And widget "core:lever" is registered from mod "core"

# Non-goals:
#   - Concrete content types (themes, tiles, buildings, classes, items,
#     quests) and stats.json: #8 and later issues.
#   - In-game UI for browsing, enabling or disabling mods. Every mod under
#     mods/ loads, unconditionally.
#   - mod.json's displayName/version/description: only "id" and "dependsOn"
#     exist; anything else is an unknown field (see the manifest outline).
#   - Packaging a mods/ folder with the uberjar/installers.
#   - How veil.main shows a load error; the playtest covers it.
#   - Two manifests with the same id: already an error, and impossible
#     without breaking the folder-name rule first, so it has no scenario of
#     its own (Clarifications, Q4).
#
# Risks:
#   - Steps must drive the pure pipeline (parse -> validate -> order ->
#     register) over in-memory file data. Only the directory walk is impure,
#     so it gets a unit spec against a temp dir, not a Gherkin scenario.
#   - The "widget"/"gadget" fixture types and their spec (label, colors/r...)
#     exist only in the acceptance steps. If they leak into src/, #8's first
#     real type will have to delete them.
#   - Error messages come from clojure.spec explain-data. Turning its paths
#     into "/colors/BORDER/r" and its predicates into "an integer" is our own
#     translation, and it's the part most likely to regress.
#   - The namespace rule and the collision rule overlap: a non-overriding
#     file in another mod's namespace is reported as a collision (naming both
#     mods) when the ID already exists, and as a namespace violation when it
#     doesn't. Both checks therefore run at registration time, in load
#     order, not during per-file validation.
#   - A new veil.mods layer needs new dependency-checker.edn edges, which
#     puts this change on the high-risk path.
#   - "the widget file" in the ID-pattern outline and the namespace/override
#     scenarios means the file's mods-relative path (e.g.
#     "core/widgets/<name>.json"); the steps pick the file name, so they
#     must assert that same path.
#
# Open questions: none. Settled in two grilling rounds, recorded in
#   specs/intent/mod-loader.md's Clarifications.
