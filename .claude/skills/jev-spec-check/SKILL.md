---
name: jev-spec-check
description: Check a specs/intent/<slug>.md against its specs/features/<slug>.feature with Jev before the approval gate - a coverage matrix of which requirements no scenario covers and which scenarios no requirement asked for, plus implementation-leak and one-concept checks, and which workflow path the change belongs on. Use after spec-feature has produced a .feature, before asking for human approval.
---

`.claude/workflow.md` Step 3 stops and waits for human approval with nothing
computed for the human to look at. This skill computes it. It **verifies**; it
never writes an intent doc or a `.feature` — Jev returns probabilities and
choices, never prose. Part of the spike in `docs/jev-spike.md`; advisory only.

Read both files first. Extract the intent doc's requirements as a numbered list
(one per distinct behaviour it asks for, not one per bullet), and the
`.feature`'s scenario names. Show the user that list before sending anything:
a coverage matrix over requirements you mis-split is worse than none.

## 1. Coverage, both directions

Forward — one payload per requirement, `id` its number. The candidate answers
are every scenario in the file plus `none`; a scenario you leave out is one the
model cannot pick.

```json
{"id": "R3",
 "model": "jev-latest",
 "state": {"requirement": "<the requirement, verbatim>",
           "intent_context": "<the surrounding section, so the requirement is not judged bare>",
           "scenarios": [{"name": "...", "steps": "<the Gherkin, verbatim>"}]},
 "questions": {
   "covered_by": {"type": "choice",
     "instructions": "Which scenario checks that this requirement holds? Answer none if no scenario would fail were the requirement not implemented.",
     "criteria": {"none": "No scenario would fail if this requirement were dropped.",
                  "<scenario name>": "<its steps>"}}}}
```

Backward — one payload per scenario, asking whether the intent doc asked for it
at all. This is the direction that catches scope the agent invented, and
`.claude/workflow.md` is explicit that `.feature` derives from intent and never
the reverse.

```json
{"id": "S2",
 "model": "jev-latest",
 "state": {"scenario": {"name": "...", "steps": "..."},
           "intent_document": "<the whole doc>"},
 "questions": {
   "asked_for": {"type": "noul",
     "instructions": "Does the intent document ask for the behaviour this scenario checks?",
     "criteria": {"true": "The document states this behaviour, or it follows directly from something it states.",
                  "false": "The scenario checks something the document never asks for."}},
   "observable": {"type": "noul",
     "instructions": "Do the scenario's steps describe behaviour a player or a caller could observe, rather than how the code is built?",
     "criteria": {"true": "Steps name inputs and observable outcomes.",
                  "false": "Steps name namespaces, functions, keys in the state map, or call order - things that change under refactoring without the behaviour changing."}}}}
```

## 2. One concept per file

`CLAUDE.md` requires one `.feature` per distinct concept, with the `Feature:`
block as the source of truth for what it covers.

```json
{"questions": {
  "one_concept": {"type": "noul",
    "instructions": "Do the scenarios in this file cover more than one distinct concept, such that they would be better split into separate .feature files?",
    "criteria": {"true": "Scenarios fall into two or more groups that share no behaviour and would each stand alone.",
                 "false": "Every scenario is an aspect of the one concept the Feature block describes."}},
  "block_accurate": {"type": "noul",
    "instructions": "Does the `Feature:` description block accurately state what these scenarios cover, and exclude what they do not?",
    "criteria": {"true": "Anyone reading only the block would correctly predict which scenarios are here.",
                 "false": "The block promises behaviour no scenario checks, or the scenarios go beyond what it describes."}}}}
```

## 3. Which workflow path

`.claude/workflow.md` sets two triggers for the high-risk path and says: if
genuinely unsure, take it, because an unnecessary approval gate is cheaper than
a skipped one. That documented asymmetry is why the threshold here is
deliberately low — **flag at 0.3**, not 0.5.

```json
{"questions": {
  "persistence": {"type": "noul",
    "instructions": "Does this change alter the format of settings.json or any saved file, such that a file written by the current version would be read differently?",
    "criteria": {"true": "A field is added, removed, renamed or re-interpreted in a persisted file.",
                 "false": "Persisted files are untouched, or only their in-memory use changes."}},
  "gate": {"type": "noul",
    "instructions": "Does this change alter a quality gate's threshold or scope - check-clean.sh, the CRAP limit, mutation limits, dependency-checker.edn, or the instruction budgets?",
    "criteria": {"true": "A limit, an allowed exception, or the set of files a gate judges changes.",
                 "false": "Gates judge the same things by the same limits afterwards."}}}}
```

## Reporting

Lead with the coverage matrix, since it is the thing a human cannot do quickly:

```
Requirements with no scenario:
  R3  "Esc returns to the main menu from Options"   (none, conf 0.78)

Scenarios not asked for by the intent doc:
  S5  "Theme reloads when the file changes"          (asked_for 0.08)

Implementation leak:
  S2  "Then :active-theme is core:default"           (observable 0.11)
```

Then the one-concept and path answers in a line each. Then say plainly what
this does **not** tell you: nothing here checks that the scenarios are *right*,
only that they and the intent doc agree. The human approval gate still stands;
this only means the human is reading a diff of the disagreements instead of
both documents.
