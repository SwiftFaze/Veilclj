# Instruction files

`CLAUDE.md`, `.claude/workflow.md`, `.claude/subagent-delegation.md` and the
skills load into agent context, so every line costs something on every task
that loads it. They decay by accretion: one-off fixes get appended and nothing
is ever removed.

## Budgets

CI-enforced by `bash .claude/tools/check-instruction-budget.sh` (the
`instruction-budget` job in `repo-hygiene.yml`), tiered by how often a file is
loaded:

| File | Loaded | Budget (lines) |
|---|---|---|
| `CLAUDE.md` | every session | 100 |
| `.claude/workflow.md` | every pipeline step | 150 |
| `.claude/subagent-delegation.md` | on dispatch | 90 |
| `.claude/skills/*/SKILL.md` | on skill use | 215 |
| `docs/*.md` | on demand | 250 |

Ratchet: when a file is trimmed, lower its number in the script in the same
commit. Never raise one without saying why in the commit message.

## Rules that became tooling

Record of prose rules replaced by a check, so they aren't re-added as prose:

| Was | Now |
|---|---|
| "Always put `Closes #N` in the PR body" | `repo-hygiene.yml` `pr-body-closes-issue` |
| "Never hand-edit the version or changelog" | `repo-hygiene.yml` `no-manual-release-edits` |
| "Keep instruction files short" | `check-instruction-budget.sh` |
| "No commented-out code / TODOs" | `check-clean.sh` section 2 |

## Monthly re-audit

1. Run the budget script with `--verbose`; anything at 90%+ gets consolidated.
2. For each tagged rule (`<!-- added YYYY-MM-DD: why -->`), check the reason
   still holds. Delete the rule if it doesn't.
3. Any rule that is mechanically checkable: write the check, delete the prose,
   add a row above.
