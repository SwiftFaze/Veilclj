# Subagent delegation rules

Mechanics and failure modes of handing Steps 4/5/7 of `.claude/workflow.md` to a
subagent. Read before dispatching or resuming a Step 4/5/7 agent or a fork.

## Choosing agent type and model

- **Default to a fresh agent pinned to Haiku 4.5, not `/fork`.** Haiku's cost
  plus a tight, self-contained prompt beats a fork's "free" inherited context
  running on the pricier parent model. The orchestrator explores once and
  compresses the result into the prompt: every file path with line numbers, the
  actual code being referenced (not just its name), and the decisions already
  made.
- **Tell the agent explicitly not to explore beyond the files listed.** If
  something it needs is missing or wrong, it stops and reports what's missing;
  the orchestrator supplies it and resumes. This constraint is what makes the
  cheaper model actually cheaper.
- **Fall back to `/fork` only when the context genuinely can't be compressed
  economically** — material too sprawling to excerpt without the prompt-writing
  costing as much as forking. Exception, not default.
- **Parallel Step 4 work:** when several tickets are ready, one fresh Haiku
  agent per ticket, each in its own git worktree.
- **Stay in the same agent across Steps 4→5→7** for one ticket — they're a
  single continuous handoff, and Steps 5 and 7 both need to know exactly what
  was implemented. Re-briefing a blank-context agent forces it to re-derive the
  change from the diff. The only reason to start fresh is genuine isolation
  (parallel worktrees).

## Verifying what comes back

**Never relay a subagent's "done" report as fact without checking it yourself.**
A summary describes what the agent intended to do, not what it did. Before
treating any step as finished: open the file it claims to have produced, run the
command it claims passed, read the actual diff. Confidence and detail in a
report are not evidence.

This rule is unconditional because a near-maximal defensive prompt didn't
prevent it: on issue #136 (fullscreen/windowed toggle), a Step 4 handoff with
full file contents, exact line numbers, literal code to paste, an explicit
"don't explore" instruction and a required verification checklist still produced
an agent that explored anyway, burned the budget, and self-reported done when it
wasn't. A detailed handoff reduces the frequency; it doesn't replace checking.

**Run the gate yourself first — it is one command.**

```
bash .claude/tools/check-clean.sh
```

This is the identical command the agent was required to pass, so there is no
gap between what it checked and what you verify. Then read its report for:
exit 0; a disposition for every **advisory** finding; and a PASS/FAIL *with
evidence naming a file, function or test* on every judgment-checklist line. A
checklist returned without evidence is the same signal as a skipped step. If
the agent worked in a worktree, run it there. Details: `docs/clean-code-gate.md`.

Specific checks:

- **A passing metric isn't evidence unless its scope is confirmed.** A mutation
  score can be genuinely green while measuring the wrong code — e.g. computed
  before `pom.xml`'s `targetClasses` was updated to include the new classes.
  Confirm what a reported number actually covers.
- **A mandatory step can't be silently skipped, downgraded, or rationalized
  away.** An agent that can't complete one should stop and report the blocker,
  not proceed with a caveat or substitute a weaker check. A report mentioning a
  skipped mandatory step is a first-class finding, not a footnote.
- **A green acceptance suite doesn't prove keyboard/focus behavior.** If the
  feature involves focus crossing a window or component boundary, confirm at
  least one scenario exercises real input, not just the `ActionMap` shortcut —
  see `docs/testing-acceptance.md`.

## Escalation when verification finds a real problem

1. **First failure** — resume the same agent via `SendMessage` with the specific
   problem, the fix, and evidence (actual error, diff, file content) — not "this
   didn't work, try again." Most corrections land here.
2. **Second failure of the same class** — switch to `/fork`, which inherits the
   diagnosis. Only after a second same-class failure, not the first.
3. **If the fork fails the same check** — the problem is in the diagnosis, not
   the executing agent. Stop and reconsider.

A fork is expensive and inherits a large conversation, so give it a tight,
closeable checklist with the cheapest, most failure-prone confirmations first —
if it runs out of budget mid-checklist, the highest-value checks are still done.
