# Development workflow (spec-first agentic pipeline)

Two paths, chosen by risk:

- **Standard path (the default, and what nearly every Veil feature uses)** —
  Steps 1, 4, 5, 6, 7. Write a short intent doc, then implement in an agile
  loop: build a slice, look at it, reconcile the intent doc with what was
  actually built, continue. The `.feature` file gets written during Steps 4-5
  as behavior stabilizes; it must exist, be wired, and pass before done.
- **High-risk path** — adds blocking Steps 2-3 (approved Gherkin spec before
  any code). Triggers in this repo: a change to the **save/settings
  persistence format** (breaks existing players' files), or to a **quality
  gate's threshold or scope** (`check-clean.sh`, CRAP/mutation limits,
  `dependency-checker.edn`). One trigger is enough. If genuinely unsure, take
  the high-risk path — an unnecessary approval gate is cheaper than a skipped
  one.

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
4. **Implementation** (`coder`, `.claude/agents/coder.md`) — **read
   `.claude/orchestrator.md` before dispatching.** It covers the coder →
   commit → hardener handoff, prompt contents, and verifying what comes back.
   The Clean Code gate is the hardener's (`.claude/agents/hardener.md`), not
   this step's.
    - **Test-first.** Write the failing speclj `it` before the code that
      passes it (`bb spec -a` reruns on save). Uncle Bob's three laws of TDD
      are the default loop here, not an aspiration.
    - **Respect the layer direction** in `docs/architecture.md`:
      `veil.main → veil.ui → veil.game`, and `veil.game` never touches Quil,
      I/O or atoms. Fix a violation by moving the code or passing data in,
      never by weakening the rule.
    - **Rendering changes can't be verified by specs** — specs cover the state
      that drawing reads, not the pixels. Say explicitly in the report which
      screens changed so the human playtest (`CLAUDE.md` Step 4.5) covers them.
    - **Behavior a key sequence can exercise gets a QA procedure**, written at
      spec time next to the `.feature`: `specs/qa/<slug>.keys` (the keys) and
      `specs/qa/<slug>.edn` (the log entries expected). Formats and the event
      vocabulary: `docs/testing.md`, "QA runs".
5. **Acceptance tests** (`coder`, same agent as Step 4) — wire the `.feature`
   file into the acceptance pipeline (`bb acceptance`) so it's executable, not
   documentation: add step handlers under `acceptance/veil/acceptance/steps/`.
   Steps drive the pure game state (`veil.game`), never the Quil window.
   **Before the human playtest, run `bb qa <slug>`** (or `bb qa --all`): it
   plays the procedure's keys through `veil.main`'s real key handler and checks
   the log. It opens a window, so it is a local step, not part of
   `check-clean.sh` or CI. A pass means input reaches the outcomes the
   procedure expects; it says nothing about rendering or feel, so the human
   playtest stays mandatory but narrower: how it *feels* and *looks*.
6. **Mutation testing** (`hardener`, after the gate is clean) — `bb mutate <file>` on each
   changed `veil.game`/`veil.ui` source file, and `bb acceptance-mutate` when a
   `.feature` changed. This is the check on the specs, since they aren't
   reviewed. Survivors get a spec (or a sharper example), not a shrug. Confirm
   the run targeted the files the change actually touched.
7. **Documentation** (`hardener`, same agent as Step 6) — part of done, not
   cleanup:
    - New domain concept, non-obvious design decision, or a deviation from an
      existing pattern → add/update an entry in `docs/`.
    - Added/renamed a `.feature` file, or changed what one covers → update that
      file's own `Feature:` description block (what it covers, what it
      supersedes, what's out of scope). There is no separate index to sync.
    - If nothing user-facing or architecturally significant changed, say so
      explicitly rather than skipping silently.
    - Narrative documentation goes in `docs/`, never into `CLAUDE.md`.

## Constraints

Enforced mechanically by `check-clean.sh` and CI, so don't re-derive them by
eye (details: `docs/testing.md`):

- Specs and acceptance tests pass; specs are well-formed (SCRAP).
- CRAP score ≤ 8 per function (`quality-gates.edn`) — complexity is only
  tolerable when it is covered.
- Layer direction `veil.main → veil.ui → veil.game` (`dependency-checker.edn`).
- No commented-out code, deferred-work markers or suppressions in added lines.
- Loosening any of the above fails the `quality-gate-ratchet` CI job.

If a change can't meet a limit, stop and propose a decomposition rather than
disabling the check. These thresholds are a deliberate dial for agent-authored
code, not a constant. If you change one, record the new value and the reasoning
here — don't loosen a gate because one change didn't fit under it.

**SLAP (Single Level of Abstraction) is not enforced** — no tool backs it here.
It's design guidance from the `uncle-bob-craft` checklist the hardener
applies (`.claude/agents/hardener.md`); don't
describe it as a build gate.

## Session management

Checkpoint at clean boundaries — specs green, nothing mid-edit — rather than
waiting for auto-compact to fire mid-thought. The agent can't run `/compact` or
`/clear` itself, so **surface the checkpoint to the user**: at the end of each
step, and in Steps 4-5 after each namespace or scenario is verified working,
state what's done and suggest clearing.

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
| Unit specs                  | Agent        | No, by design                               |
| Mutation test results       | Tooling      | Human skims summary                         |
| Codebase docs               | Agent        | Spot check                                  |

## Notes for the agent

- If the intent doc is missing or ambiguous, use the `grilling` skill rather
  than inventing scope — it batches open questions into dependency-ordered
  rounds, which fits the intent/spec loop better than a flat prompt. Reserve
  `AskUserQuestion` for a genuinely standalone multiple-choice pick.
- Never mark a feature done without: `check-clean.sh` at exit 0 with the
  judgment checklist answered, acceptance tests passing, the Step 7
  documentation decision stated explicitly, `bb qa <slug>` passing where a QA
  procedure exists, the human playtest (`CLAUDE.md` Step 4.5), and the linked
  issue closed (`CLAUDE.md` Step 7.5). A gate you
  cannot pass is a blocker to report, never a rule to suppress.
