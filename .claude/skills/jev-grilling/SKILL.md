---
name: jev-grilling
description: Run a grilling session where each answer the user gives is checked by Jev against the whole settled design tree before the frontier advances - catching answers that hedge, contradict an earlier round, or assume a decision that is still open. Use when the user wants their thinking stress-tested and wants the consistency of their own answers checked, not just the questions asked.
---

Run the `grilling` skill exactly as written — design tree, frontier, rounds,
numbered questions each with your recommended answer. This skill changes one
thing: **the user's answers are checked before the frontier advances.**

You never answer for the user. Jev judges what they said; it does not replace
it. Part of the spike in `docs/jev-spike.md`; it never blocks, and the user can
wave any flag away.

## After each round

The user answers. Before you recompute the frontier, build one payload per
answered question and send them together.

**The state must carry the whole session, not the one pair.** A
`(question, answer)` pair on its own cannot show that an answer contradicts
Round 1, or leans on something still open — that is most of the value here.
Every payload carries the same `subject` and `settled`, and differs only in
`current`.

```json
{"id": "R2Q3",
 "model": "jev-latest",
 "state": {
   "subject": {"what": "<the plan being grilled, in full>",
               "goal": "<what it is for>",
               "constraints": ["<each known constraint>"]},
   "settled": [{"id": "R1Q2", "question": "<asked>", "decision": "<what the user settled>"}],
   "open": [{"id": "R2Q4", "question": "<asked this round, not yet judged>"}],
   "current": {"id": "R2Q3", "question": "<asked>", "answer": "<the user's words, verbatim>"}
 },
 "questions": {
   "settles": {"type": "noul",
     "instructions": "Does `current.answer` settle `current.question` - state a decision the plan can be built on?",
     "criteria": {"true": "A decision is made and is specific enough to act on.",
                  "false": "It hedges, defers, restates the question, answers a different question, or lists options without picking one."}},
   "contradicts": {"type": "noul",
     "instructions": "Does `current.answer` contradict any decision already recorded in `settled`?",
     "criteria": {"true": "It requires something a settled decision ruled out, or rules out something a settled decision requires.",
                  "false": "It is consistent with every settled decision, or is unrelated to all of them."}},
   "contradicted": {"type": "choice",
     "instructions": "Which decision in `settled` does `current.answer` contradict? Answer none if none.",
     "criteria": {"none": "No settled decision is contradicted.",
                  "R1Q2": "<that decision, restated>"}},
   "assumes_open": {"type": "noul",
     "instructions": "Does `current.answer` depend on a question in `open` that is not yet settled?",
     "criteria": {"true": "It only holds if some still-open question resolves a particular way, and the answer does not say so.",
                  "false": "It stands on the subject and the settled decisions alone, or it names the assumption explicitly."}}
 }}
```

Write the payloads one per line to a scratch `.jsonl`, run `bb jev <file>`, and
match responses by `id`.

Verbatim matters. Summarising the user's answer into the payload judges your
paraphrase, and a hedge is exactly what a paraphrase tidies away.

## What to do with the answers

| Signal | Threshold | Effect |
|---|---|---|
| `settles` low | < 0.5 | Frontier holds. Re-ask, naming what is still undecided. |
| `contradicts` high | >= 0.7 | Frontier holds. Show both decisions and ask which one stands. |
| `assumes_open` high | >= 0.7 | Frontier holds. The question belonged to a later round: say so, and move it. |
| all clear | — | The decision is settled. Advance. |

`contradicted` only ever supplies the evidence for a `contradicts` flag — never
the verdict. A `choice` picks its nearest option even when nothing is really
contradicted, so reading it alone invents conflicts.

Between 0.4 and 0.7, mention it in one clause and advance anyway. Below that,
say nothing: a grilling session that queries every answer is just slower.

Report a round like this, then continue:

```
Q3  Should mod load failure be fatal?
    You: "probably fatal, we can revisit"
    ! 0.81 does not settle it - hedged, no decision recorded

Q4  Where does the theme registry live?
    You: "in the state map under :themes"
    ok

Frontier holds on Q3. Q4 advances.
```

The session is done when the frontier is empty **and** no answer is still
flagged. Do not act on the plan until the user confirms.
