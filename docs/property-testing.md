# Property specs (test.check)

Example specs check the cases someone thought of; a property states an
invariant and lets [test.check](https://github.com/clojure/test.check) hunt for
an input that breaks it. Run them deliberately - by the hardener, when touching
a namespace that has a real invariant - not as part of `check-clean.sh`.

```
bb property                                  # every spec in spec-property/
VEIL_PROPERTY_SEED=<n> bb property           # rerun from a seed (Git Bash)
$env:VEIL_PROPERTY_SEED=<n>; bb property     # the same in PowerShell
```

**What makes a good property.** A real invariant over generated input:
something that must hold for *every* input, such as a bound or "always a real
item" (menu selection is always an index into the menu's items), a round trip,
idempotence, conservation, or an ordering. Add one where such an invariant
exists, never to raise a coverage number.

**Generators build `veil.game` state and game inputs only** - the `:up :down
:confirm :back nil` vocabulary `handle-input` takes, never Quil, I/O or raw
key events - so a property never needs `veil.ui` (`architecture.md`, layers).

**Where they live.** `spec-property/` mirrors `src/` like `spec/` does:
`spec-property/veil/game/state_property_spec.clj` holds the properties for
`veil.game.state`. They are speclj specs that call `quick-check` from an `it`.
They are kept out of `bb spec`, `bb cov` / `bb crap`, `bb mutate` and
`bb acceptance-mutate`: they are slow and random, and those runs must stay fast
and repeatable. `spec/` remains the only speclj root for all of those.

**Running one.** Each `it` passes the property to `veil.property-support/run`,
which returns nil when it holds, else a message for `should-be-nil` to print:

- **Trials.** 100 per property, one shared constant,
  `veil.property-support/default-trials`. There is no override; a soak run is a
  one-line edit of that constant.
- **On failure** the message gives the trial count, the seed, the shrunk
  counterexample and the rerun command. Shrinking stops at a local minimum, so
  the counterexample is small but not always the shortest possible.
- **Rerun** with `VEIL_PROPERTY_SEED=<n>` as above; with it set, the properties
  run from that seed instead of the clock. `veil.property-support` is itself
  specced (`spec-property/veil/property_support_spec.clj`) against a
  deliberately failing property.

`bb scrap` defaults to `spec/`; run `bb scrap spec-property` to check these
files' structure.
