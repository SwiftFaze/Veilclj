---
name: jev-spec-auto
description: Run the whole pipeline unattended from a GitHub issue number to a green PR - every question spec-intent or grilling would put to the human is answered by Jev instead, the implementation goes through implement-issue, and CI failures are diagnosed and fixed in place. Stops only at the merge. Use when the user wants a hands-off run from issue to reviewable PR.
model: opus
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

## Human gates move to the PR; none are deleted

The run goes from an issue number to a green PR with nobody at the keyboard.
It does not remove this repo's human gates — it **relocates every one of them
to the PR**, where they are owed before merge instead of mid-run.

| Gate | Normally | Here |
|---|---|---|
| Step 3, spec approval (high-risk path) | blocks before any code | PR is labelled `needs-spec-approval`, and the body says which trigger fired and at what probability |
| Step 4.5, human playtest | blocks before the hardener | PR body lists exactly which screens and behaviours are unplayed, under a heading that says Step 4.5 is outstanding |
| Merge | human | human, unchanged |

This is a deliberate deviation from `CLAUDE.md`'s ordering, and the reason to
be loud about it in the PR body: a playtest owed and named is a moved gate, a
playtest unmentioned is a skipped one. Never write that Step 4.5 is done.

## The loop

1. **Ground it.** `spec-intent` unchanged: resolve the issue, branch off
   `develop`, move the tracker item. Then search the codebase for what the
   idea touches — Jev answers from the state you give it, so a thin state is
   the failure you will actually hit.
2. **Derive the intent doc** from the issue, `spec-intent`'s way.
3. **Answer the open questions** (below), appending each decision to the doc.
4. **Write the `.feature`** with `spec-feature`.
5. **Run `jev-spec-check`.** Its §3 sets the path: on high-risk, take the
   high-risk path's artifacts in order and carry the label forward — do not
   stop.
6. **Close the gaps it found**, then re-run it. **At most three rounds.** If
   round three still has uncovered requirements, stop and report them: a check
   that keeps failing is telling you the intent doc is ambiguous, and looping
   harder will not fix that.
7. **Hand to `implement-issue`** for Steps 4-7, skipping its playtest stop and
   recording what the playtest owes instead. **The agents still run.** Steps
   4-5 are the `coder`'s and Step 6-7 the `hardener`'s, dispatched and
   verified exactly as `implement-issue` says. Do not implement inline: this
   skill replaces the human in the loop, not the pipeline's own division of
   labour, and the hardener's gate is the only thing standing between an
   unattended run and unreviewed code.
8. **Push and open the PR** against `develop`, never `master`. `Closes #N` in
   the body is required — `pr-body-closes-issue` enforces it, and without it
   nothing closes the issue on merge.
9. **Watch CI and fix it** (below).
10. **Green: done.** Report and wait. Do not merge.

## Watching CI

```bash
gh pr checks <n> --watch --repo SwiftFaze/Veilclj
gh run view <run-id> --log-failed --repo SwiftFaze/Veilclj
```

The jobs are `branch-name`, `master-source-check` and `build-and-test`
(`ci.yml`), plus `instruction-budget`, `pr-body-closes-issue`,
`no-manual-release-edits` and `quality-gate-ratchet` (`repo-hygiene.yml`).

**At most three fix rounds.** Each round: read the failing log, classify it,
fix, push, watch again. After the third, stop and report with the log — a
failure that survives three attempts is one you do not understand, and a
fourth guess is how a gate ends up suppressed.

Two jobs are never "fixed" by making them pass. `quality-gate-ratchet` fails
because a gate was loosened: the fix is to restore the gate and change the
code. `master-source-check` fails because the PR targets `master`: the fix is
the base branch, and if it is already `develop`, something is structurally
wrong — stop.

Before pushing any CI fix, put it to Jev:

```json
{"id": "fix1", "model": "jev-latest",
 "state": {"failing_job": "build-and-test",
           "log_excerpt": "<the failing output>",
           "proposed_fix": "<the diff you are about to push>",
           "rule": "A gate you cannot pass is a blocker to report, never a rule to suppress."},
 "questions": {
   "suppresses": {"type": "noul",
     "instructions": "Does `proposed_fix` make the check stop complaining without fixing what it complained about?",
     "criteria": {"true": "It raises a limit, adds an exception or suppression, deletes or weakens the assertion, narrows what the check looks at, or removes the failing test.",
                  "false": "It changes the code under test so the original check now passes unchanged."}},
   "addresses": {"type": "noul",
     "instructions": "Would `proposed_fix` actually make `failing_job` pass, judged against `log_excerpt`?",
     "criteria": {"true": "It addresses the cause the log names.",
                  "false": "It changes something the log does not implicate."}}}}
```

`suppresses >= 0.5` — do not push it. Stop and report the failure and the fix
you rejected. `addresses < 0.5` — you have not found the cause yet; read more
log rather than pushing a guess.

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

Lead with the PR URL and CI state. Then the decision table, fallbacks first —
they are the ones worth a human's attention. Then what `jev-spec-check` still
flagged at the last round, and every human gate the PR now owes.

Say plainly what the run does not establish: that the decisions were *right*.
It establishes that they were made from the stated evidence, recorded with
their confidence, and consistent with each other. The playtest and the PR
review are still where correctness gets decided.
