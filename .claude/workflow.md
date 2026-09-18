# Development workflow (spec-first agentic pipeline)

Two paths, chosen by risk:

- **Standard path (the default, and what nearly every Veil feature uses)** —
  Steps 1, 4, 5, 6, 7. Write a short intent doc, then implement in an agile
  loop: build a slice, look at it, reconcile the intent doc with what was
  actually built, continue. The `.feature` file gets written during Steps 4-5
  as behavior stabilizes; it must exist, be wired, and pass before done.
- **High-risk path** — adds blocking Steps 2-3 (approved Gherkin spec before
  any code). Triggers in this repo: a change to the **save/settings
  persistence format**, the **mod-loading JSON contract** (anything that could
  break existing mods), or the **`DrawableAsciiEntity`/`Inspectable` public
  contracts** third-party content depends on. One trigger is enough. If
  genuinely unsure, take the high-risk path — an unnecessary approval gate is
  cheaper than a skipped one. The mod-loading JSON contract's *shape* is
  schema-validated (`docs/schemas/`, checked against `mods/core/**`), but an
  intentional change to the contract is still high-risk — CI catching drift
  doesn't move the gate.

The split exists because a fully pre-approved spec doesn't survive contact with
implementation; plans made before an agent starts reliably diverge from what
gets built. Pay the approval latency only where being wrong is expensive.

## Steps

1. **Intent** (Sonnet 5) — `specs/intent/<slug>.md` exists before code. If it
   doesn't, ask rather than guess. From a GitHub issue, use the `spec-intent`
   skill. Intent is not frozen: clarifying answers gathered later get appended
   here, and `.feature` is always derived from this file, never the reverse.
   Use extended thinking ("think hard") for the judgment-heavy parts — scope
   boundaries, what's in/out — not for reading files or running `gh`.
2. **Spec — high-risk path only** (Sonnet 5) — generate
   `specs/features/<slug>.feature` from the intent doc via the `spec-feature`
   skill. No implementation code. Loops with Step 1: update `intent.md` first,
   regenerate `.feature`, then ask what's still open.
3. **Human approval — high-risk path only** — stop and wait. Do not proceed on
   your own.
4. **Implementation** (Haiku 4.5) — **read `.claude/subagent-delegation.md`
   before dispatching.** It covers agent type/model, handoff prompt contents,
   staying in one agent across 4→5→7, and verifying what comes back.
    - **The Clean Code gate is mandatory and blocking.** You may not report
      this step finished until `bash .claude/tools/check-clean.sh` exits 0 and
      you have answered every line of the judgment checklist it prints, with
      evidence. It is the same command the orchestrator runs to verify you.
      <!-- added 2026-09-06: replaces the hand-run PMD loop. develop is red
           from pre-existing violations, so the gate scopes to added lines. -->
      Rules, thresholds and carve-outs: `docs/clean-code-gate.md`.
    - **Respect the module dependency direction** — `ModuleDependencyTest`
      (ArchUnit) fails the build if engine code depends on
      `com.swiftfaze.veil.ui`, or a `ui.widget` class depends on a screen in
      `com.swiftfaze.veil.ui`. Fix by inverting the dependency or extracting an
      interface, never by weakening the rule.
    - The gate's checklist mechanizes `uncle-bob-craft`; read that skill for the
      design lens. Neither makes implementation code human-reviewed.
    - **UI work:** the handoff prompt must include `docs/ui-styling.md` itself,
      not a secondhand summary — Step 4 agents are told not to explore.
    - **Swing rendering/layout/sizing/text changes must be visually verified**
      before the step is done — `mvn test` passing proves the code runs, not
      that it renders correctly; no test here asserts on pixels or rendered
      text. Technique: `docs/ui-verification.md`. This is the agent checking
      itself; it does not replace the human playtest (`CLAUDE.md` Step 4.5).
5. **Acceptance tests** (Haiku 4.5, same agent as Step 4) — wire the `.feature`
   file to the runner so it's executable, not documentation.
      <!-- added 2026-09-06: the duplicate-step check that used to be a manual
           grep here is now NoDuplicateStepDefinitionsTest, so the prose is gone.
           Background on why duplicates cascade: docs/testing.md. -->
    - **If you touched a shared step-definitions file** (e.g.
      `UiComponentFrameworkSteps.java`, which backs several features), run
      `mvn clean verify` **twice** and require identical results. One green run
      isn't evidence when shared test infrastructure changed.
6. **Mutation testing** (tooling, no model) — run it against new/changed code.
   This is the check on the unit tests, since they aren't reviewed. Confirm
   `pom.xml`'s `targetClasses` actually includes the feature's new classes
   before trusting the score.
7. **Documentation** (Haiku 4.5, same agent as Steps 4-5) — part of done, not
   cleanup:
    - New domain concept, non-obvious design decision, or a deviation from an
      existing pattern → add/update an entry in `docs/`.
    - Player-visible game data → the GitHub wiki too (`docs/wiki.md`).
    - Added/renamed a `.feature` file, or changed what one covers → update that
      file's own `Feature:` description block (what it covers, what it
      supersedes, what's out of scope). There is no separate index to sync.
    - If nothing user-facing or architecturally significant changed, say so
      explicitly rather than skipping silently.
    - Narrative documentation goes in `docs/`, never into `CLAUDE.md`.

## Constraints (CI-enforced, not optional)

Configured in `.pmd-minimal.xml` and `pom.xml`; they fail the build, so don't
re-derive them by eye:

- Max function length 40 lines; max cyclomatic complexity 8; max parameters 4
  (PMD).
- Minimum line coverage 85% repo-wide (JaCoCo).
- Module dependency direction (ArchUnit, `ModuleDependencyTest`) — see Step 4
  and `docs/testing-module-dependency.md`.

If a change can't meet a limit, stop and propose a decomposition rather than
disabling the check. **One narrow exception:** a method overriding a JDK/library
interface whose signature mandates 5+ parameters may carry
`@SuppressWarnings` — precedent documented in `docs/testing-quality-gates.md`
§ "Code quality gates". It applies to parameter count only, never to
complexity, length, coverage, or the module rule.

**SLAP (Single Level of Abstraction) is not enforced** — no tool backs it here.
It's design guidance from the `uncle-bob-craft` checklist in Step 4; don't
describe it as a build gate.

These thresholds are a deliberate dial for agent-authored code, not a constant.
If you change one, record the new value and the reasoning here — don't loosen a
gate because one change didn't fit under it.

## Session management

Checkpoint at clean boundaries — tests green, nothing mid-edit — rather than
waiting for auto-compact to fire mid-thought. The agent can't run `/compact` or
`/clear` itself, so **surface the checkpoint to the user**: at the end of each
step, and in Steps 4-5 after each file or scenario is verified working, state
what's done and suggest clearing.

Between steps, prefer a fresh session once the step's artifact is on disk — the
next step's real memory is that artifact, not the conversation. Before clearing,
write a short status note in the PR description or commit message: which step
finished, which files were touched, what the next step does first. On `/resume`,
read that note plus the git diff and the `.feature` file before opening anything
else.

## What gets reviewed vs. not

| Artifact                    | Written by   | Reviewed by human?                          |
|-----------------------------|--------------|---------------------------------------------|
| Intent doc                  | Human/agent  | N/A — it's the source                       |
| Gherkin acceptance spec     | Agent        | High-risk: before implementation. Standard: after the fact |
| Implementation code         | Agent        | No, by design — leverage comes from not reading it |
| Unit tests                  | Agent        | No, by design                               |
| Mutation test results       | Tooling      | Human skims summary                         |
| Codebase docs, wiki         | Agent        | Spot check                                  |

## Notes for the agent

- If the intent doc is missing or ambiguous, use the `grilling` skill rather
  than inventing scope — it batches open questions into dependency-ordered
  rounds, which fits the intent/spec loop better than a flat prompt. Reserve
  `AskUserQuestion` for a genuinely standalone multiple-choice pick.
- Never mark a feature done without: `check-clean.sh` at exit 0 with the
  judgment checklist answered, acceptance tests passing, the Step 7
  documentation decision stated explicitly, the human playtest (`CLAUDE.md`
  Step 4.5), and the linked issue closed (`CLAUDE.md` Step 7.5). A gate you
  cannot pass is a blocker to report, never a rule to suppress.
