# Orchestrator rules

For the session running the pipeline: handing Steps 4-7 of `.claude/workflow.md`
to the `coder` and `hardener` subagents, and checking what comes back. Read
before dispatching or resuming either one, or a fork. Each role's model, tools,
ownership and reading scope live in `.claude/agents/`; don't restate them.

## Dispatching

<!-- added 2026-09-19: Steps 4-7 split into two roles so neither is overloaded (#23) -->
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

## Escalation when verification finds a real problem

The ladder applies to each role separately.

1. **First failure.** Resume the same agent via `SendMessage` with the specific
   problem, the fix, and evidence (actual error, diff, file content), not "this
   didn't work, try again." Most corrections land here.
2. **Second failure of the same class.** Switch to `/fork`, which inherits the
   diagnosis. Only after a second same-class failure, not the first.
3. **If the fork fails the same check,** the problem is in the diagnosis, not
   the executing agent. Stop and reconsider.

<!-- added 2026-09-19: hardener must not change behavior; resuming beats re-briefing (#23) -->
**Hardener → coder hand-back.** When the hardener reports that a gate needs a
behavior change, that's a handoff, not a failure. Resume the *original* coder
via `SendMessage` with the hardener's finding (it still holds the
implementation context, so this is cheaper than briefing a new coder), verify
its new commit, then resume the hardener with the new sha.

Give a fork a tight checklist, cheapest and most failure-prone checks first, so
the highest-value ones are done even if it runs out of budget.
