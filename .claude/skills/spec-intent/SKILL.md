---
name: spec-intent
description: Given a GitHub issue number, bootstrap Step 1 of the spec-first workflow — create a linked branch off develop, move the tracker item to In progress, and derive specs/intent/<slug>.md from the issue's own description. Use when the user hands you an issue number to start work from, instead of asking them to write the intent doc by hand.
---

Entry point into this repo's spec-first pipeline (root `CLAUDE.md`) when
the intent already lives in a GitHub issue rather than being dictated
fresh. Produces the same `specs/intent/<slug>.md` Step 1 normally requires
— `/spec-feature` picks up from there exactly as it would for a
hand-written intent doc. Do not write a `.feature` file or any
implementation code in this skill.

Takes one input: a GitHub issue number (or URL). If not given, ask for it
— don't guess which issue.

## Step 1 — Read the issue

```
gh issue view <n> --json number,title,body,url,labels,state
```

If `state` is `CLOSED`, tell the user and confirm before continuing —
don't silently start work on a closed issue.

## Step 2 — Derive the slug and branch name

- **Slug:** kebab-case the issue title (strip punctuation, lowercase,
  hyphens for spaces). This must match the eventual
  `specs/features/<slug>.feature` name, so keep it short and concept-level
  — same rule as the intent-doc template ("one intent -> one slug per
  distinct concept"). If the title clearly bundles more than one unrelated
  concept, say so and ask whether to split before continuing, rather than
  picking one slug and burying the rest.
- **Branch prefix**, from the issue's labels:
  - `bug` label -> `fix/`
  - `documentation` label -> `docs/`
  - anything else (including `enhancement`, or no recognized label) ->
    `feat/` (matches the `feat:` Conventional Commits type this repo
    already uses for commit messages — see root `CLAUDE.md`)
  - State which prefix you picked and why in the final report — it's a
    one-word rename if the user disagrees, not worth a blocking question.
- **Branch name:** `<prefix><slug>`.
- Check `git branch -a` for a collision on that name first. If it already
  exists (local or remote), stop and ask rather than reusing or
  overwriting it — it likely represents existing work.

## Step 3 — Create and link the branch

First, run `git status`. If the working tree isn't clean, stop and tell
the user to commit or stash before continuing — don't carry uncommitted
changes onto a new branch silently.

Then, in one step, create the branch off `develop` (the integration branch
this repo's feature/bug/task work always forks from — see root
`CLAUDE.md`'s branch protection notes; only `hotfix/*` branches off
`master` instead, and only for urgent production fixes, which this flow
isn't for) and link it to the issue via GitHub's Development panel:

```
gh issue develop <n> --name <branch> --base develop --checkout
```

This both creates and checks out the branch, and associates it with the
issue (visible in the issue's "Development" section on GitHub) — no
separate linking step needed.

## Step 4 — Move the tracker item to In progress

The issue must be on the VEIL project board (project number 2, owner
`SwiftFaze`) before its status can be set. Add it if it isn't already
there (ignore an "already exists" error from this call — it just means it
was already added, e.g. by `brainstorm-issue`):

```
gh project item-add 2 --owner SwiftFaze --url <issue-url>
```

Then set its status:

```
gh project item-edit 2 --owner SwiftFaze --url <issue-url> --field "Status" --value "In progress"
```

(Exact option name is `In progress`, lowercase p — match it exactly.)

## Step 5 — Derive specs/intent/<slug>.md from the issue

Write `specs/intent/<slug>.md` using `specs/intent/TEMPLATE.md`'s
structure:

- **Slug(s):** `<slug>`
- **Author:** current git user (`git config user.name`)
- **Date:** today
- **Source:** `[GitHub issue #<n>](<url>)`
- **Status:** only "Intent drafted" checked; everything else unchecked.
- **Problem / Scope / Actors / Desired behavior / Constraints / Open
  questions:** derived from the issue's title and body.
  - If the issue was written in intent-doc shape already (e.g. via the
    `brainstorm-issue` skill — Problem/Scope/Actors/Desired
    behavior/Constraints/Open questions headings), this is close to a
    direct carry-over into the template's frontmatter, not a rewrite.
  - If the issue is terse (a one-line bug report, a short ask with no
    structure), derive what you can from it and the codebase, and use the
    `grilling` skill to ask whatever's genuinely missing or ambiguous —
    per `.claude/workflow.md`'s "Notes for the agent": never invent scope to
    fill a section. Most terse issues only produce one open question, so
    this is usually a single-question round, but let grilling ask more if
    the terseness turns out to hide several open forks rather than
    assuming it's always just one.

Do not commit this file — leave it for the user to review/commit, same as
any other intent doc.

## Step 6 — Report back

One short summary: branch name (created + checked out), issue linked and
moved to In progress, and the intent doc's path. Tell the user the next
step is `/spec-feature <slug>` once they're happy with the intent doc —
don't start that step yourself.
