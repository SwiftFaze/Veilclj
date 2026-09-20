# The Jev spike

**Status: under evaluation.** Nothing here gates. Every tool exits 0 whatever
it finds, and no CI job runs any of them. The question this spike answers is
whether a calibrated typed judgment is good enough to keep — if the answer
turns out to be no, delete this file, the `jev-*` skills, `.claude/workflow-jev.md`
and the four `tools/veil_tools/jev*`/`single_answer*` namespaces.

## Why a judgment model at all

Several of this repo's rules are real, load-bearing, and undetectable by any
tool it runs. `docs/clean-code-gate.md` calls them the judgment checklist and
says outright that they cannot be automated; today the agent that wrote the
code also grades itself against them. A System One model (TypeSafe's Jev)
returns a **probability**, a **choice from a fixed set**, or a **score on a
scale** — never prose — so code can consume the answer and the repo can hold a
threshold instead of a vibe.

What it is *not* for: Jev cannot generate text, so it can never write code, an
intent doc or a `.feature`. It judges; Claude writes.

## Transport

```
bb jev <file>
```

Posts System One payloads and prints the answers. One JSON object in the file
gives one pretty response; one object per line (JSONL) gives one response per
line, in order, each echoing back the payload's own `"id"` so a caller can
match them up. Requests in a JSONL file go out in parallel. Exit 1 if any call
failed, so a script can tell a verdict from a dead network.

Deliberately dumb: the judgement design — what to ask, which primitive, what
the criteria say, how to compose the answers — lives in the skill that writes
the payload. A new kind of check is a new question, not a new namespace.

Needs `TYPESAFE_API_KEY` in the environment. A missing key or a failed call is
reported as an error, never a silent pass: an advisory check must not be
switchable off by unplugging the network.

## `bb single-answer`

The one judgment-checklist line with a tool behind it.
[`docs/architecture.md`](architecture.md#layers) says `veil.ui` translates and
does not decide: when `veil.game` already answers a question, `veil.ui` calls
it. Working the answer out again is a defect even though every dependency
arrow still points downward, because the rule now exists twice and the copies
drift. `dependency-checker` cannot see it — the arrow is fine.

For each function in the `veil.ui` files in scope, the check sends the function
source plus every public `veil.game` function and its docstring, and asks two
questions over that one state:

- a **noul** — does this work out an answer one of those already gives?
- a **choice** — which one, or `none`?

The noul is the verdict; the choice is the evidence the checklist line demands
("name the `veil.ui` fn and the `veil.game` fn"). Using the choice as a verdict
would produce false positives: a function that correctly *calls*
`state/selected-item` still has it as its nearest neighbour.

```
bb single-answer                      # veil.ui files changed vs origin/develop
bb single-answer --all                # every veil.ui function
bb single-answer --threshold 0.0      # show every score (default floor 0.30)
bb single-answer src/veil/ui/view.clj # explicit paths
```

**Measured, 2026-09-20.** Five deliberately planted violations scored 0.95-0.97
and each named the correct `veil.game` function; five clean presentation
functions in the same fixture scored 0.05-0.12; all 82 real `veil.ui` functions
scored below 0.20, the highest being `view/lines-for-screen` at 0.19. The
0.83-wide empty band between clean and violating means the threshold is not
load-bearing: anything from 0.3 to 0.9 gives the same answer. About 2.6k input
tokens per function judged.

**What that does not yet prove.** The fixture and the questions were written by
the same author, and planted violations are blunter than real ones. The
outstanding validation is a real violation from this repo's history: run the
check on the parent of a commit that fixed one and see whether it fires.

## The skills

Each holds the question design for one workflow step; all three call `bb jev`.

| Skill | Judges | Composition |
|---|---|---|
| `jev-grilling` | each answer you give during a `grilling` session, against the whole settled design tree | holds the frontier on answers that hedge, contradict an earlier round, or assume something still open |
| `jev-brainstorm` | the judgement calls in `brainstorm-issue` / `brainstorm-milestone`: the split, milestone fit, priority, duplicates, and **pairwise** build order | code topologically sorts the pairwise edges into the order the issues get filed in |
| `jev-spec-check` | `specs/intent/<slug>.md` against `specs/features/<slug>.feature` before the approval gate | a coverage matrix: which requirements no scenario covers, and which scenarios no requirement asked for |
| `jev-spec-auto` | every question `spec-intent` or `grilling` would put to the human, during an unattended Steps 1-2 run | adopts a pick only above a confidence floor **and** only when the state held the answer; otherwise falls back to the documented conservative default and records it as an assumption |

`jev-spec-auto` is the one to be most careful with. Because Jev picks from a
fixed set, Claude writes both the question and the candidate answers, so an
unattended run can only ever choose an option Claude thought of — the failure
mode is a confident pick between three options where the right fourth was
never written down. Its `answerable` noul exists for the related trap: a choice
is confident whenever its options are far apart, including when the state never
contained the answer. Judge that variant on its fallbacks, not its decisions.

`jev-brainstorm` is the one that most clearly beats a prompt. Asking an agent to
order a dozen issues in one pass is inconsistent run to run; asking one bounded
question per pair and letting code sort the edges is reproducible given the
edges, and surfaces genuine ambiguity as a cycle instead of an arbitrary
tie-break. It matters at brainstorm time rather than later because
`brainstorm-milestone` files issues in build order and cross-links them, so a
wrong order has to be unpicked by hand.

## `.claude/workflow-jev.md`

A parallel copy of the pipeline with these checks slotted in at the steps they
serve. Separate on purpose: `.claude/workflow.md` stays the real one until the
spike is judged, so nothing here can silently become process.
