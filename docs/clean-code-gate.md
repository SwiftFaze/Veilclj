# The Clean Code gate

```
bash .claude/tools/check-clean.sh
```

One command, run identically by the implementing agent before it reports done
and by the orchestrator verifying that report, and by CI on every PR. It
orchestrates Uncle Bob's tools (`docs/testing.md`) and adds what none of them
covers: the CRAP ceiling, the docs check, the text-smell checks, which findings
block versus advise, and the judgment checklist.

The tool gates judge the whole repo (it started clean, so any red is yours);
the text-smell check judges only the lines your change added versus `develop`,
and the docs check's QA-procedure half only the feature files your change added.

## Sections

| # | Check | Blocks? |
|---|---|---|
| 1 | `bb spec` passes | yes |
| 2 | `bb scrap`: no speclj structure errors (e.g. `it` inside `it`) | yes |
| 3 | `bb acceptance`: every feature scenario passes | yes |
| 4 | `bb crap` + `bb crap-gate`: every function CRAP ≤ `quality-gates.edn` `:crap-max` | yes |
| 5 | `bb layers`: no layer violations or cycles | yes |
| 6 | `bb dry`: duplicate-code candidates | advisory |
| 7 | Docs check (`veil-tools.docs-check`): every `bb` task is mentioned in `docs/testing.md`; every feature added on the branch has a QA procedure or a `QA: none - <reason>` line | advisory |
| 8 | Added lines: no commented-out forms (`;; (…)`, `#_(…)`), TODO/FIXME/XXX/HACK, or `clj-kondo/ignore` | yes |

An **advisory** finding doesn't fail the gate, but each one needs a disposition
in the completion report: fix it, or one line on why it's correct as written.
For section 7 the fix is nearly always the doc (`docs/testing.md`, "Docs check"),
not the check.

`--fast` skips the JVM-heavy sections (1-4, 6) for the inner loop. A `--fast`
run never counts as passing the gate. A full run takes about 30 seconds.

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
