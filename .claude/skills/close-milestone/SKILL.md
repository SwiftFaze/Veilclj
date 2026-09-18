---
name: close-milestone
description: Close a finished VEIL milestone and promote it to release — verifies every issue in the milestone is closed and no PR is still awaiting merge into develop, closes the milestone, then opens/merges the develop-into-master promotion PR that triggers the real release build (Release Please + cross-platform installers). Use when the user says a milestone is done and should ship, not for filing or auditing milestones (that's brainstorm-milestone/audit-planning).
---

Closing counterpart to `brainstorm-milestone` (which files milestones) and
`audit-planning` (which health-checks them). This skill ships one: it is the
last step in a milestone's life, not a health check, so it changes state
(closes the milestone, merges into `master`) rather than just reporting.

Takes one input: a milestone number or title (matching the `"<n>. <Title>"`
convention documented in `audit-planning`). If not given, list open
milestones and ask which one — this is a standalone, self-contained pick
with nothing else riding on it, so use `AskUserQuestion`, not `grilling`:

```
gh api repos/SwiftFaze/Veil/milestones --jq '.[] | select(.state=="open") | {number, title, open_issues, closed_issues}'
```

**Known CI gap to watch for (Step 6):** the `branch-name` job in
`.github/workflows/ci.yml` (a required check on `master`) has no exemption
for a literal `develop` head branch, unlike its sibling `master-source-check`
job in the same file, which does exempt it. A `develop`→`master` PR can
therefore fail that required check even though root `CLAUDE.md` documents
`develop`→`master` as a valid path into `master`. This hasn't been fixed yet
(flagged, left for a separate change) — Step 6 below checks for exactly this
failure and reports it clearly instead of leaving the PR stuck with no
explanation.

## Step 1 — Resolve the target milestone

Match the input against the open-milestones list from above: by number
directly, or by title (exact match first, then substring). If nothing
matches, or more than one title substring-matches, stop and ask rather than
guessing which milestone was meant.

## Step 2 — Verify every issue in the milestone is closed

```
gh api repos/SwiftFaze/Veil/milestones/<n> --jq '{open_issues, closed_issues}'
```

If `open_issues` is 0, continue. Otherwise list the offending issues and
stop — do not close a milestone with open work still tracked under it:

```
gh issue list --repo SwiftFaze/Veil --milestone "<n>. <title>" --state open --json number,title,url
```

Report each one's number/title/url and tell the user to close or re-milestone
them first (per root `CLAUDE.md`'s Step 7.5, an issue's PR merging doesn't
auto-close it here since PRs land on `develop`, not the repo's default
branch — it may just be waiting on that manual close).

## Step 3 — Verify no PR is awaiting merge into develop

Any open PR targeting `develop` is unfinished work that hasn't reached
`master` yet, independent of which milestone it's filed under — promoting
now would ship without it and make it awkward to land after the fact.

```
gh pr list --repo SwiftFaze/Veil --base develop --state open --json number,title,headRefName,url,isDraft
```

If any come back (draft or ready-for-review, doesn't matter), list them and
stop. Tell the user to merge, close, or explicitly defer each one before
re-running this skill — don't decide that for them.

## Step 4 — Confirm before touching master

Everything past this point is hard to reverse and visible to others: closing
the milestone, and — if there's anything to promote — merging into `master`,
which cascades into a real GitHub Release and cross-platform installer build
(see `docs/release.md`). Confirm once with `AskUserQuestion` before
proceeding, stating plainly what will happen: "Close milestone `<n>. <title>`
(all `<closed_issues>` issues done) and merge `develop` into `master`,
triggering Release Please + the installer build. Proceed?" Recommend
"Proceed" as the default option since Steps 2-3 already passed.

## Step 5 — Close the milestone

```
gh api repos/SwiftFaze/Veil/milestones/<n> -X PATCH -f state=closed
```

## Step 6 — Promote develop into master

First check there's actually something to promote:

```
git fetch origin
git log origin/master..origin/develop --oneline
```

If that's empty, `master` is already even with `develop` — tell the user
there's no build to trigger, milestone closure alone is the complete outcome
this run, and stop here (don't open an empty PR).

Otherwise check there isn't already a stale promotion attempt open:

```
gh pr list --repo SwiftFaze/Veil --base master --state open --json number,title,headRefName,url
```

If one exists, stop and point the user at it instead of opening a duplicate.

Open the promotion PR — the title/body is the mechanism for referencing the
milestone in what triggers the build (see the note below on why this is the
practical ceiling for that ask):

```
gh pr create --repo SwiftFaze/Veil --base master --head develop \
  --title "chore: promote milestone <n>. <title> to release" \
  --body "Promotes develop into master, closing out milestone #<n> — \"<title>\" (<closed_issues> issues). Triggers Release Please's stable release + cross-platform installer build on merge.

Closed issues: <#a, #b, #c, ...>"
```

Then merge it the same way Release Please's own release PR merges itself
(per `docs/release.md`): wait for required checks rather than force through
them —

```
gh pr merge <number> --repo SwiftFaze/Veil --merge --auto
```

**If this fails or the PR shows the `branch-name` check red:** this is the
known CI gap flagged at the top of this skill — the job doesn't recognize a
literal `develop` head branch as allowed. Report this to the user plainly
(quote the check's failure message), and suggest fixing
`.github/workflows/ci.yml`'s `branch-name` job to exempt `develop` the same
way `master-source-check` already does, in a small separate PR — don't try
to route around a required check (no `--admin` merge, no disabling the
check) to force this one through.

## Step 7 — Best-effort: record the release on the milestone

Optional, and skip it without ceremony if it doesn't pan out — the user's
own ask was explicit that this doesn't need to work. The milestone itself
can't carry a changelog entry (Release Please only picks up individual
Conventional Commit messages already merged from `develop`, not the
promotion PR's title), but its own description field can note which version
it shipped in, which is a real, durable reference.

Poll for the resulting release, a handful of times a couple minutes apart
(the build takes a few minutes — build-and-test, then the installer matrix):

```
gh release list --repo SwiftFaze/Veil --limit 1 --json tagName,publishedAt,url
```

If a new stable tag (`vX.Y.Z`, no `-beta.` suffix) appears whose
`publishedAt` is after this run started, append a line to the milestone's
existing description (don't overwrite it):

```
gh api repos/SwiftFaze/Veil/milestones/<n> -X PATCH -f description="<existing description>

Shipped in <tagName> — <release url>"
```

If nothing shows up within a handful of checks, stop polling and just tell
the user the milestone closed and the promotion merged, but the release
hadn't published yet as of this run — they can check `gh release list` and
patch the milestone description themselves later if they want that record.

## Step 8 — Report back

One short summary: milestone closed (number/title), whether anything was
promoted (PR number + merge result, or "nothing to promote"), and whether
Step 7's release note landed or was left for later. If Step 2, 3, or 6 (the
CI gap) stopped the run early, that's the report instead — state exactly
what's blocking and what the user needs to do before re-running.
