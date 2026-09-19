---
name: spec-intent-auto
description: Run the entire standard-path spec-first pipeline (intent -> spec -> implementation -> acceptance tests -> mutation testing -> docs) unattended for a simple feature, from a GitHub issue number straight through to "ready for playtest" — answering trivial ambiguities itself and front-loading any real ones, so the only human checkpoint is one final playtest right before the PR is opened. Use for small, well-scoped issues that don't need the user present for the whole run — not for auth/payments/data-integrity/public-API work, which stays on the high-risk path's blocking gates.
---

Unattended variant of this repo's spec-first pipeline (`.claude/workflow.md`,
root `CLAUDE.md`). Where `spec-intent` + `spec-feature` + the Step 4-7
handoff still stop to ask the user everything, and the repo's normal Step
4.5 playtest sits mid-pipeline, this skill collapses all of that into one
continuous run with exactly **one** human checkpoint: a final playtest,
positioned right before the PR is opened instead of between Steps 4 and 5.
Everything before that checkpoint happens without asking the user to
confirm intermediate steps — the same "don't re-confirm mechanical steps"
instinct that already applies to this repo's other pipelines, just made
the explicit default for this skill rather than a judgment call.

This is a deliberate, scoped deviation from the normal step order — don't
generalize it back into `.claude/workflow.md` or the other pipeline
skills. It only applies within a run of this skill.

**Only for the standard path.** Takes one input: a GitHub issue number (or
URL), same as `spec-intent`. If not given, ask for it.

## Step 0 — Classify risk before doing anything else

Read the issue (`gh issue view <n> --json number,title,body,url,labels,state`)
and classify it per `.claude/workflow.md`'s "Notes for the agent": auth,
payments, data integrity, or public APIs is high-risk — one matching
characteristic is enough, default to high-risk if genuinely unsure.

If it's high-risk, **stop immediately** and tell the user this skill is
standard-path only — point them at `spec-intent` + `spec-feature` instead,
which preserve the real human approval gate that class of feature needs.
Do not proceed, do not ask "are you sure" — just decline and explain why.

If `state` is `CLOSED`, tell the user and confirm before continuing.

## Step 1 — Intent, branch, and board (same as spec-intent)

Follow `spec-intent`'s Steps 2-5 exactly: derive the slug, pick the
branch prefix from labels, check for a branch-name collision, create and
link the branch off `develop`, add the issue to the VEILCLJ project board
(project 2, owner `SwiftFaze`) and move it to `In progress`, and derive
`specs/intent/<slug>.md` from the issue body.

## Step 2 — Resolve open questions: answer trivial ones, front-load real ones

This is the one place in the whole run where the user might hear from you
before the final playtest — and it should happen now, in one pass, not be
discovered mid-implementation later.

For each open question the intent doc (or your own read of the codebase)
surfaces, classify it:

- **Trivial — decide it yourself** when the answer is a mechanical default
  already established elsewhere: an existing pattern in the codebase to
  follow 1:1, a naming/formatting convention, a choice with no real
  downstream consequence, or something fully answerable by reading the
  code/docs. Record your decision and reasoning in `intent.md`'s
  `## Clarifications` section (same format `spec-feature` uses) but
  labeled `A (auto-decided):` instead of `A:`, so a later human reviewer
  can tell at a glance which answers were never actually asked.
- **Real — ask now, all at once** when the answer changes scope,
  user-facing behavior, data shape, or has more than one reasonable design
  with genuinely different tradeoffs. Use the `grilling` skill for this —
  one full round (or the minimum number of dependency-ordered rounds), not
  a question dribbled out later. **When in doubt, treat it as real and
  ask** — a wrong autonomous guess here is expensive precisely because
  nothing checks it again until the playtest at the very end.

After any real-question round is answered, update `intent.md`'s
Clarifications section per the normal `A:` format before continuing.

Do not proceed past this step with any open question left silently
unresolved either way.

## Step 3 — Feature spec (same as spec-feature, no approval gate)

Follow `spec-feature`'s Step 2 to generate `specs/features/<slug>.feature`
from the now-settled `intent.md`.

Since Step 0 already confirmed this is standard-path work, there is no
Step 3 approval gate to wait for (`.claude/workflow.md`'s Step 3 is
high-risk-path only) — move straight to implementation.

## Step 4 — Implementation, then hardening: coder → commit → hardener

Run the `implement-issue` skill's sequence (`.claude/skills/implement-issue/`):
its steps 1-3 (coder, verify, `bb qa`) and step 5 (hardener, verify). Your
prompt carries only the ticket's context: explicit file paths with line
numbers, the actual referenced code (not just names), and the reasoning
already settled in `intent.md` and the `.feature` file.

**Skip that skill's step 4 playtest stop and its step 6 PR — do not have the
agent or yourself pause for the playtest here.** This skill deliberately
relocates the playtest: it happens once, at the very end (Step 6 below), and
the PR is opened by this skill's Step 7.

## Step 5 — Verify each handoff yourself

Per "Verifying what comes back" in the `implement-issue` skill: do not
relay either agent's "done" report as fact. Check the coder's commit before
dispatching the hardener; after the hardener, independently open the files
it changed, re-run `bash .claude/tools/check-clean.sh` yourself, and
confirm its mutation runs targeted the changed files. If something's wrong,
or the hardener hands back a behavior change, follow that file's escalation
section.

## Step 6 — Stop here: the one human checkpoint

This is the only point in the run where you wait for the user. Report,
in one message:

- Branch name and what issue/slug it covers.
- A short summary of what was implemented (not a full diff dump).
- Confirmation that `check-clean.sh` is green (specs, Clean Code
  checks, judgment checklist) and mutation testing has been skimmed.
- Any `A (auto-decided):` entries from Step 2, so the human can see what
  was decided without them, not just what was asked.
- Explicit instructions for the playtest: `bb play`, and
  what specifically to try given what changed.
- That you're waiting for either "looks good" or a bug report before
  going further — nothing is committed or pushed yet.

Do not commit, push, or open a PR before this confirmation. If the user
reports a problem instead, fix it, re-run the relevant checks from Step 5,
and ask for the playtest again — don't silently re-expand scope while
you're at it.

## Step 7 — Commit, push, open the PR

Once the user confirms the playtest passed:

1. Update `intent.md`'s Status checklist (Implemented, Manually
   playtested, Acceptance tests passing, Mutation testing passed,
   Documentation updated — all now true).
2. Stage and commit with a Conventional Commits message, ending with this
   session's attribution footer (see system reminder — do not use any
   other attribution).
3. Push the branch.
4. Open the PR against `develop` (never `master` — see root `CLAUDE.md`'s
   branch protection notes), referencing the source issue (`Closes #<n>`
   only applies once merged into the actual default branch this repo uses
   for auto-close, which per root `CLAUDE.md`'s Step 7.5 is not automatic
   here — still write "Relates to #<n>" in the body for traceability, but
   tell the user Step 7.5's manual `gh issue close` is still needed after
   merge, same as every other feature PR in this repo).
5. Report the PR URL and stop. Closing the issue (Step 7.5) and merging
   are still separate, later, human-driven actions outside this skill's
   scope.
