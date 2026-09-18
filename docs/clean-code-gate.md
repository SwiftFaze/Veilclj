# The Clean Code gate

```
bash .claude/tools/check-clean.sh
```

One command, run identically by the implementing agent before it reports done
and by the orchestrator verifying that report. It judges only the lines your
change added versus `develop` (plus untracked files), so existing debt is never
attributed to whoever touched the file next.

## Sections

| # | Check | Blocks? |
|---|---|---|
| 1 | `bb spec` passes | yes |
| 2 | No commented-out forms (`;; (…)`, `#_(…)`), no TODO/FIXME/XXX/HACK, no new `clj-kondo/ignore` | yes |

More sections (speclj structure, CRAP score, dependency direction) are added as
the Uncle Bob tooling lands - see `testing.md`.

`--fast` skips section 1 for the inner loop. A `--fast` run never counts as
passing the gate.

## Judgment checklist

After the mechanical checks pass, the script prints a checklist that cannot be
automated: SLAP, SRP, Purity, Naming, Why-not-what, Test intent, AAA, and No new
debt. Each line needs PASS/FAIL **plus evidence naming a file, function or
spec**. A checklist without evidence counts as a skipped step.

Worked examples for the Clojure-specific lines:

- **Purity** - `(defn select [menu] (assoc menu :chosen (current-item menu)))`
  is pure; the same function calling `q/text` or `swap!` is not, and belongs in
  `veil.ui` or `veil.main`.
- **SLAP** - a `key-pressed` handler that calls `(move-selection menu :down)`
  is orchestration; one that does `(mod (inc idx) (count items))` inline mixes
  in detail. Extract the detail into a named function.

## If you can't pass it

Stop and report the blocker. Suppressing a rule, or reporting done "with a
caveat", is never the fix.
