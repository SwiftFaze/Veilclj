---
name: jev-brainstorm
description: Run brainstorm-issue or brainstorm-milestone with Jev behind the judgement calls - the split, the milestone fit, the priority, duplicates against open issues, and the build order between issues, which is composed by code from pairwise judgements rather than ranked in one pass. Use when the user wants to brainstorm an idea or a feature arc into GitHub issues and wants the decisions checked rather than guessed.
---

Follow `brainstorm-issue` (one idea) or `brainstorm-milestone` (a feature arc)
exactly as written — every step, the same issue bodies, the same milestone
conventions, the same board and priority handling. Pick between them by the
same rule they give: decomposition being part of the work means the milestone
one.

This skill changes two things:

- **Step 2 uses `jev-grilling`, not `grilling`.** Same design tree and rounds;
  each answer is also checked against the settled tree before the frontier
  advances.
- **The judgement calls below get a Jev answer first.** It is a recommendation,
  never a decision. Where the base skill says to confirm with
  `AskUserQuestion`, still confirm — Jev fills in which option you recommend,
  it does not remove the question.

Part of the spike in `docs/jev-spike.md`. Transport is `bb jev <file>`
(JSONL, one payload per line, `id` to match responses). If it is unavailable,
run the base skill unchanged and say so.

## Where Jev comes in

| Base skill step | Judgement | Primitive |
|---|---|---|
| issue §2 / milestone §2 | Does the idea split into more than one issue? | noul |
| milestone §2 | Is the arc actually just one task? (hand back to `brainstorm-issue`) | noul |
| milestone §2 | Is each proposed issue independently completable? | noul per issue |
| both §2 | Priority, against the P2 default | score |
| both, before filing | Does an open issue already cover this? | choice over open issues + `none` |
| issue §4 / milestone §3 | Which milestone does it belong to? | choice over milestones + `none` |
| milestone §2 and §4 | Build order between issues | pairwise choice, sorted in code |
| milestone §4 | Does each issue body name what it depends on? | noul |

## Milestone fit

`brainstorm-issue` §4 gives the failure mode this exists to catch: a
quest-*content* idea does not belong in "Mod-loader restructure" just because
it mentions quests — that milestone is about the loading pipeline, not content
built on it. Keyword overlap is exactly what gets this wrong, so the state
carries each milestone's **description**, and `none` is always an option; the
base skill's "don't force a fit" rule stands.

```json
{"id": "fit", "model": "jev-latest",
 "state": {"idea": {"title": "...", "problem": "...", "in_scope": "..."},
           "milestones": [{"title": "1. Mod-loader restructure", "description": "..."}]},
 "questions": {
   "milestone": {"type": "choice",
     "instructions": "Which milestone's arc does this idea's work belong to? Judge the work against each milestone's described goal, not against words the two happen to share.",
     "criteria": {"none": "No milestone's arc covers this; it should stay unmilestoned or start a new one.",
                  "1. Mod-loader restructure": "<that description>"}}}}
```

Confidence below 0.6 is itself the finding: the descriptions do not separate
the arcs. Offer `none` and say why.

## Duplicates

Not in the base skills, and worth the one call before filing. State carries the
drafted idea and every open issue's number, title and body.

```json
{"questions": {
  "duplicate_of": {"type": "choice",
    "instructions": "Which open issue already asks for this work? Answer none if none of them do; overlapping subject matter is not a duplicate unless the work is the same.",
    "criteria": {"none": "No open issue asks for this work.", "42": "<that issue's title and body>"}}}}
```

## Build order

`brainstorm-milestone` §4 files issues in build order and cross-links each to
what it depends on, so the order has to be right before anything is filed —
getting it wrong means rewriting bodies and numbers.

Ask one **unordered** question per pair, with three outcomes. Include the
drafted issues, and any open issue in the target or adjacent milestone that is
not built yet: a new piece can depend on planned work as easily as on another
new piece, which is `brainstorm-issue` §1's "partly blocked" case.

```json
{"id": "draft2|#27", "model": "jev-latest",
 "state": {"a": {"ref": "draft2", "title": "...", "scope": "..."},
           "b": {"ref": "#27", "title": "...", "scope": "...", "status": "open, not started"}},
 "questions": {
   "order": {"type": "choice",
     "instructions": "Which of these has to be built first? Judge only technical dependency: whether one's work could not start, or would have to be redone, if the other landed later.",
     "criteria": {"a_first": "b builds on something a creates, or b would be rewritten if a landed after it.",
                  "b_first": "a builds on something b creates, or a would be rewritten if b landed after it.",
                  "independent": "Either order works; neither rests on the other."}}}}
```

Then compose **in code, not by eye** — this is the whole reason to ask pairwise
rather than for a ranking:

1. Keep an edge only where the choice is not `independent` and confidence is
   `>= 0.6`. A guessed edge is worse than no edge.
2. Topologically sort the kept edges; break remaining ties by the user's own
   preference from Step 2.
3. Report any cycle. A cycle is not a model failure — it is two issues that
   each assume the other, which is a real decomposition problem and belongs in
   the next `jev-grilling` round, not in a tie-break.

Pairs grow as n²: six drafts plus four open issues is 45 calls, ~1k tokens
each. Put the resulting order to the user before filing anything.

## Reporting

`brainstorm-issue` §6 / `brainstorm-milestone` §6 unchanged, plus one line
naming any judgement where Jev and the user disagreed and the user's answer
stood. That line is the spike's evidence; without it there is nothing to judge
the check on later.
