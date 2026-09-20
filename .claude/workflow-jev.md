# Development workflow — the Jev variant

**This is not the pipeline.** `.claude/workflow.md` is. This is a parallel copy
under evaluation (`docs/jev-spike.md`), kept separate so nothing here becomes
process by drift. Follow it only when the user asks for the Jev variant by
name.

Every step below is `.claude/workflow.md`'s step, unchanged, plus one advisory
check. **No check here can block, fail a build, or overrule a human.** Each
produces a probability and a disposition; the human decides. If a check is
down, the step proceeds exactly as `.claude/workflow.md` says — a missing
judgment is never a pass.

## What changes, step by step

| Step | Unchanged | Added |
|---|---|---|
| 1. Intent | `spec-intent`, or the doc by hand | `jev-spec-check` §3 routes standard vs high-risk. Flags at 0.3, not 0.5: `.claude/workflow.md` says take the high-risk path when unsure, so the threshold follows that asymmetry. |
| 1b. Grilling | `grilling` when intent is ambiguous | `jev-grilling` instead: same rounds, but each answer is checked against the whole settled tree before the frontier advances. |
| 2. Spec | `spec-feature` writes the `.feature` | `jev-spec-check` §1-2: coverage both directions, implementation leak, one concept per file. |
| 3. Approval | human approves; still blocking | The human reads a diff of the disagreements instead of both documents. **The gate does not move.** |
| 4-5. Implementation | `implement-issue`, coder, `bb qa` | — nothing at the step. The `coder` may call Jev itself; see below. |
| 4.5. Playtest | human plays `bb play` | — nothing, and nothing ever. |
| 6. Mutation | `bb mutate`, `bb acceptance-mutate` | — nothing. Survivors are already a mechanical fact. |
| 7. Docs | the hardener's docs decision | — nothing. |
| Gate | `check-clean.sh`, all nine sections | `bb single-answer` as an advisory tenth, for the one judgment-checklist line no tool covers. Exits 0 always. |

## The unattended variant

`jev-spec-auto` runs Steps 1-2 with nobody at the keyboard: every question
`spec-intent` or `grilling` would put to the human is answered by Jev instead,
recorded in the intent doc with its confidence and the alternatives, and then
handed to `implement-issue` for Steps 4-7.

It halts at three points, and reaching one is a successful outcome: a
high-risk path detection (Step 3 is blocking), the Step 4.5 playtest, and the
PR merge. The first two are this repo's rules; the third is because merging is
outward-facing and hard to undo.

Claude generates each question and its candidate answers; Jev picks between
them. So an unattended run can only ever choose an option Claude thought of —
which is the thing to watch when judging whether the variant is worth keeping.

## The agents may use Jev in either workflow

`coder` and `hardener` can reach for `bb single-answer` and `bb jev` in an
ordinary `.claude/workflow.md` run, not only a Jev one — their own agent files
say so. It never gates for them either: an answer they disagree with is an
answer they overrule, and the evidence on a checklist line stays theirs to
write. That is why nothing is added at Steps 4-7 here. The tooling reaches the
agents through the agents, not through a second pipeline.

## Why so little in Steps 4-7

Jev returns a probability, a choice from a fixed set, or a score. It cannot
emit source, a spec, a docstring or a commit message. So it cannot implement,
and it cannot replace the coder or the hardener. Where the repo already has a
mechanical answer — CRAP, layers, mutation survivors, the shell check — a
probability would be strictly worse than the fact it would sit next to.

That leaves exactly two kinds of place worth adding one: a rule the repo
states and no tool checks (the judgment checklist), and a judgment repeated
across many items where consistency matters more than depth (issue ordering,
requirement coverage, answers across grilling rounds).

## Before the pipeline

`jev-brainstorm` runs `brainstorm-issue` / `brainstorm-milestone` with Jev
behind their judgement calls, and `jev-grilling` in place of `grilling` at
their Step 2. That is pre-intent work, so it sits before Step 1 rather than at
a step. `audit-planning` and `close-milestone` are untouched.

## Judging the spike

Keep a check when, over real use: it fired on something a human agreed was
real, and it did not fire on things that were fine. Drop it when its findings
are routinely waved away — a check whose output is always dismissed is worse
than no check, because it teaches everyone to skim the section it lives in.

The evidence so far is in `docs/jev-spike.md`, including what it does not yet
prove.
