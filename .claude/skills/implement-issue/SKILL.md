---
name: implement-issue
description: Run Steps 4-7 of the spec-first pipeline on the current feature branch — dispatch the coder, verify it, run bb qa, stop for the human playtest, dispatch the hardener, verify it (gate, mutation scope, behavior diff, docs decision), and only then open the PR. Use once a .feature is committed; --from-hardener <sha> starts at hardening. Not for writing the spec (spec-feature) or resuming a dead session (resume-issue).
---

The entry point for Steps 4-7 of `.claude/workflow.md`: implementation,
hardening and the PR. Counterpart to `/spec-intent` and `/spec-feature`, which
own Steps 1-2. It owns the **sequence** and the **verification**. The work stays
in `.claude/agents/coder.md` and `hardener.md`, and the mechanical gates stay in
`check-clean.sh` and the `bb` tasks: name them here, never restate their
thresholds or how to pass them. Each agent's model, tools, ownership and
reading scope live in `.claude/agents/`; don't restate them.

Takes no required input. It works on the checked-out branch: the slug is the
branch name minus its `feat/`/`fix/`/`docs/` prefix, the issue number comes from
that slug's `specs/intent/<slug>.md` `source:` line. If either can't be read,
ask — don't guess. Optional `--from-hardener <sha>`: skip to step 5 with `<sha>`
as the baseline (a hand-written commit, or one already built and playtested).

## Sequence

<!-- added 2026-09-19: nothing fixed where the PR sits in the pipeline; a PR proposed after a spec-only push would have closed its issue on an unbuilt feature -->
1. **Refuse without a committed spec.** `specs/features/<slug>.feature` must be
   committed on this branch (`git ls-files --error-unmatch`, clean in
   `git status`). If not, stop and point to `/spec-feature`. Don't write the
   feature yourself.
2. **Dispatch `coder`, verify its commit.** Run `bb spec` and `bb acceptance`
   yourself (below).
3. **QA run.** If `specs/qa/<slug>.edn` exists, run `bb qa <slug>` yourself; a
   failing run goes back to the coder like any failed verification. If it
   doesn't exist: a `QA: none - <reason>` line in the `.feature`'s `Feature:`
   block means skip this step; otherwise the spec is incomplete, so send it
   back to `/spec-feature`, not the coder. Formats: `docs/testing.md`, "QA runs".
4. **Stop for the Step 4.5 human playtest** (`CLAUDE.md`). Say which screens
   changed (the coder's note) and what a QA pass doesn't cover. It narrows the
   playtest to feel and rendering; it doesn't replace it. Wait for the human.
5. **Dispatch `hardener`** with the coder's sha, then verify it (below),
   including the docs decision. Re-run `bb qa <slug>` after its refactor where
   a procedure exists: it's the one check that observable input→event behavior
   survived.
6. **Open the PR, only here.** Base `develop`. The body has `Closes #N` (the
   `CLAUDE.md` rule), the `bb qa` result (or that the feature opted out), and
   what the human playtest covered, in the human's words: ask, don't invent it.
   Never mention or open a PR before this step, including after a spec-only
   push. Merging is the human's (`CLAUDE.md`: squash).

With `--from-hardener`, do steps 5-6 only; every hardener verification still runs.

## Dispatching

<!-- added 2026-09-19: Steps 4-7 split into two roles so neither is overloaded -->
- **Coder → commit → hardener, one after the other, on the feature branch.**
  Dispatch `subagent_type: "coder"`; verify its commit (below); stop for the
  human playtest (`CLAUDE.md` Step 4.5); then dispatch `subagent_type:
  "hardener"` with the coder's sha. The commit is the handoff: the hardener
  starts from a clean diff, and each role's work can be checked on its own.
- **Default to these agents, not `/fork`.** A tight prompt on a pinned cheap
  model beats a fork's inherited context on the pricier parent model. Explore
  once yourself and compress it into the prompt: file paths with line numbers,
  the actual code referenced (not just its name), and the decisions already
  made. Fork only when the material is too sprawling to excerpt for less than
  forking costs.
- **Parallel tickets:** one coder per ticket in its own git worktree; that
  ticket's hardener runs in the same worktree.

## Verifying what comes back

**Never relay a subagent's "done" report as fact without checking it yourself.**
A summary describes what the agent intended to do, not what it did. Before
treating any step as finished: open the file it claims to have produced, run the
command it claims passed, read the actual diff. Confidence and detail in a
report are not evidence.

It's unconditional because a near-maximal defensive prompt didn't prevent it:
in SwiftFaze/Veil#136, a handoff with full file contents, literal code, a "don't
explore" instruction and a verification checklist still produced an agent that
explored anyway and self-reported done when it wasn't.

**After the coder:** the commit exists, `bb spec` and `bb acceptance` pass when
you run them, and the diff matches the ticket.

**After the hardener, run the gate yourself first. It's one command:**

```
bash .claude/tools/check-clean.sh
```

It's the identical command the hardener had to pass. Then read its report for:
exit 0; a disposition for every **advisory** finding; and a PASS/FAIL *with
evidence naming a file, function or test* on every judgment-checklist line. A
checklist returned without evidence is the same signal as a skipped step. Also
confirm `git diff <coder-sha>..HEAD` changes no behavior: no edited spec
expectation or `.feature` scenario. If the work was in a worktree, run it
there. Details: `docs/clean-code-gate.md`.

Specific checks:

- **A passing metric isn't evidence unless its scope is confirmed.** A mutation
  score can be genuinely green while measuring the wrong code, e.g. a
  clj-mutate run pointed at a file other than the one the change touched.
  Confirm what a reported number actually covers.
- **A mandatory step can't be silently skipped, downgraded, or rationalized
  away.** An agent that can't complete one should stop and report the blocker,
  not proceed with a caveat or substitute a weaker check. A report mentioning a
  skipped mandatory step is a first-class finding, not a footnote.
- **A green acceptance suite doesn't prove on-screen behavior.** Acceptance
  steps drive the pure game state, never the Quil window, so rendering and
  input wiring are only proven by the human playtest (`CLAUDE.md`).
- **The docs decision is checked against the diff, not the report.** Read
  `git diff <coder-sha>..HEAD --stat` and the diff itself, and hold the report
  to these; a miss goes back to the hardener naming the specific doc:
  - A `bb` task added or renamed → `docs/testing.md` describes it.
  - A contract between namespaces or layers changed → `docs/architecture.md`
    (and `docs/uml/` if affected) is updated.
  - A `.feature` added or changed → its `Feature:` block still says what it
    covers, supersedes and excludes.
  - A QA procedure added or changed → `docs/testing.md`'s "QA runs" still
    matches its format.
  - "Nothing user-facing or architecturally significant changed" → the diff
    must actually show that. It's the one claim you can't take on trust.

## Escalation when verification finds a real problem

The ladder applies to each role separately.

1. **First failure.** Resume the same agent via `SendMessage` with the specific
   problem, the fix, and evidence (actual error, diff, file content), not "this
   didn't work, try again." Most corrections land here.
2. **Second failure of the same class.** Switch to `/fork`, which inherits the
   diagnosis. Only after a second same-class failure, not the first.
3. **If the fork fails the same check,** the problem is in the diagnosis, not
   the executing agent. Stop and reconsider.

<!-- added 2026-09-19: hardener must not change behavior; resuming beats re-briefing -->
**Hardener → coder hand-back.** When the hardener reports that a gate needs a
behavior change, that's a handoff, not a failure. Resume the *original* coder
via `SendMessage` with the hardener's finding (it still holds the
implementation context, so this is cheaper than briefing a new coder), verify
its new commit, then resume the hardener with the new sha.

Give a fork a tight checklist, cheapest and most failure-prone checks first, so
the highest-value ones are done even if it runs out of budget.
