# The VEILCLJ project board

Project number `3`, owner `SwiftFaze`. Skills that file, audit or start work on
an issue (`brainstorm-issue`, `brainstorm-milestone`, `audit-planning`,
`spec-intent`) set board fields with the snippet below. It is the one canonical
copy: link here, don't restate it.

## Set a single-select field

Fields and options used here: `Status` (`Backlog`, `Ready`, `In progress`,
`In review`, `Done`) and `Priority` (`P0`, `P1`, `P2`). Names are matched
exactly, so `In progress` has a lowercase p.

```bash
N=<issue-number>; FIELD="<Status|Priority>"; OPTION="<option name>"

PROJECT=$(gh project view 3 --owner SwiftFaze --format json --jq .id)
# Adding an issue that is already on the board returns its existing item id.
ITEM=$(gh project item-add 3 --owner SwiftFaze \
  --url "https://github.com/SwiftFaze/Veilclj/issues/$N" --format json --jq .id)
FIELD_ID=$(gh project field-list 3 --owner SwiftFaze --format json \
  --jq ".fields[] | select(.name==\"$FIELD\") | .id")
OPTION_ID=$(gh project field-list 3 --owner SwiftFaze --format json \
  --jq ".fields[] | select(.name==\"$FIELD\") | .options[] | select(.name==\"$OPTION\") | .id")

[ -n "$FIELD_ID" ] && [ -n "$OPTION_ID" ] || { echo "no $FIELD option $OPTION on the board" >&2; exit 1; }
gh project item-edit --id "$ITEM" --project-id "$PROJECT" \
  --field-id "$FIELD_ID" --single-select-option-id "$OPTION_ID"
```

The lookups use `gh`'s built-in `--jq`, so nothing beyond `gh` is needed (`jq`
itself isn't installed everywhere).

The ids are looked up by name on every run, so a renamed or re-created option
can't leave a stale hardcoded id behind. Empty ids mean the name doesn't exist
on the board; without the guard, `item-edit` answers "no changes to make" and
nothing is set.

## Always read it back

`gh project item-edit` prints nothing on success and can fail without a useful
message, so a skill must confirm the write before reporting it:

```bash
gh project item-list 3 --owner SwiftFaze --format json --limit 200 \
  --jq ".items[] | select(.content.number==$N) | {status, priority}"
```

The field you set must show the value you set. If it doesn't, report the
mismatch instead of success. (`--limit 200` is the most items one call
returns; raise it if the board outgrows it.)

## Why the id form

`gh project item-edit` also has a by-name form (`item-edit 3 --owner … --url …
--field … --value …`) in recent `gh` releases, but the id form above works on
every version and is what `gh` documents for scripts.
