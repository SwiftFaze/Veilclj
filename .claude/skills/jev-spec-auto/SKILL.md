---
name: jev-spec-auto
description: Run Steps 1-2 of the spec-first pipeline unattended from a GitHub issue number - every question that spec-intent or grilling would put to the human is answered by Jev instead, with the decision, its confidence and its alternatives recorded in the intent doc. Use when the user wants a hands-off spec run; it halts at the three points no model can stand in for.
---

`spec-intent` then `spec-feature`, with nobody at the keyboard. Every question
those skills would put to the human becomes a Jev judgement, recorded so the
human can audit the whole run afterwards instead of answering during it.

Part of the spike in `docs/jev-spike.md`. Transport is `bb jev <file>`. If it
is unavailable, **stop** — degrading to Claude answering its own questions is
the one failure mode this skill must not have, because it looks identical to
success.

## Jev decides; it does not invent

Jev returns a probability, a choice from a fixed set, or a score. It cannot
write an option. So you generate the question **and** its candidate answers —
exactly the `grilling` format, question plus your recommendation plus the
alternatives — and Jev picks between them.

The consequence is the main limit of this skill: **Jev cannot choose an answer
you did not think of.** Generate the real alternatives, including the one you
disagree with, and name them concretely. Two plausible options and a straw man
produce a confident answer to the wrong question.

## Where it stops

Three halts. They are not configurable here, and reaching one is a normal,
successful outcome — report and exit.

| Halt | Why |
|---|---|
| High-risk path detected | `.claude/workflow.md` Step 3 is blocking, and its triggers are the two places being wrong is expensive. Route here on `jev-spec-check` §3 at **0.3**, per that file's own "if unsure, take the high-risk path". |
| Step 4.5 playtest | `CLAUDE.md`: no feature is done without it. It judges whether movement and rendering *feel* right — not text, and not something any model here can see. |
| PR merge | Outward-facing and hard to reverse. Open the PR; the human merges. |

## The loop

1. **Ground it.** `spec-intent` unchanged: resolve the issue, branch off
   `develop`, move the tracker item. Then search the codebase for what the
   idea touches — Jev answers from the state you give it, so a thin state is
   the failure you will actually hit.
2. **Derive the intent doc** from the issue, `spec-intent`'s way.
3. **Answer the open questions** (below), appending each decision to the doc.
4. **Write the `.feature`** with `spec-feature`.
5. **Run `jev-spec-check`.** Its §3 decides the path — halt if high-risk.
6. **Close the gaps it found**, then re-run it. **At most three rounds.** If
   round three still has uncovered requirements, halt and report them: a check
   that keeps failing is telling you the intent doc is ambiguous, and looping
   harder will not fix that.
7. **Hand to `implement-issue`**, which already runs Steps 4-7 agent-side.
   Halt at its playtest step.

## Answering one question

One payload per open question. The state carries the issue, what you found in
the codebase, and every decision already made this run — a question answered
without the earlier answers drifts out of consistency the same way a grilling
round does.

```json
{"id": "Q1", "model": "jev-latest",
 "state": {
   "issue": {"number": 61, "title": "...", "body": "..."},
   "codebase": ["veil.game.theme/color resolves a key against the active theme",
                "no namespace currently persists anything but settings.json"],
   "settled": [{"question": "<asked earlier this run>", "decision": "<what was chosen>"}],
   "question": "<the open question, as grilling would put it>",
   "options": [{"id": "a", "answer": "<concrete option>", "implication": "<what it commits the design to>"},
               {"id": "b", "answer": "<concrete option>", "implication": "..."}]},
 "questions": {
   "pick": {"type": "choice",
     "instructions": "Which option in `options` best answers `question` for this codebase, given `issue`, `codebase` and every decision in `settled`?",
     "criteria": {"a": "<that option and its implication>", "b": "..."}},
   "answerable": {"type": "noul",
     "instructions": "Do `issue` and `codebase` actually contain what is needed to answer `question`, or would any pick be a guess?",
     "criteria": {"true": "The state holds the facts the choice turns on.",
                  "false": "The answer depends on intent or context that is not present here."}},
   "scope": {"type": "noul",
     "instructions": "Does answering `question` at all mean building something the issue never asked for?",
     "criteria": {"true": "Every option adds work beyond the issue's request.",
                  "false": "The question is about how to do what the issue asks."}}}}
```

`answerable` is the one that earns its place. A `choice` is confident whenever
the options are far apart, including when the state never held the answer —
high confidence on a guess is exactly the failure this automation would
otherwise ship silently.

## Composing the answer

```
answerable < 0.5  OR  pick confidence < 0.6
    -> do not adopt the pick
    -> take the conservative default the base skill documents
       (smallest coherent scope; P2; the high-risk path)
    -> record it under "## Open questions" as an assumption, not a decision

scope >= 0.7
    -> drop the question; it is scope the issue did not ask for

otherwise
    -> adopt, and record it as a decision
```

Never break a tie at random, and never lower a threshold to get a run to
finish. Falling back to the documented default is the designed outcome, not a
failure — it is how an unattended run stays conservative when the state was
thin.

## The audit trail

`.claude/workflow.md` says the intent doc is not frozen and that later answers
get appended there. That is where the record goes, so the reviewer reads one
file:

```markdown
## Decisions (automated run, <date>)

- **<question>** -> <decision chosen>  `pick 0.86, answerable 0.91`
  Alternatives: <the options not taken>
- **<question>** -> <conservative default>  `pick 0.41 - below floor`
  Recorded as an assumption below, not a decision.
```

Every fallback also goes under `## Open questions`, because that is what it is.

## Reporting

Lead with which halt you reached and why. Then the decision table, fallbacks
first — they are the ones worth a human's attention. Then what `jev-spec-check`
still flagged at the last round.

Say plainly what the run does not establish: that the decisions were *right*.
It establishes that they were made from the stated evidence, recorded with
their confidence, and consistent with each other. The playtest and the PR
review are still where correctness gets decided.
