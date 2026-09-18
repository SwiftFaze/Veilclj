#!/usr/bin/env bash
#
# The Clean Code gate. ONE command, run identically by the subagent before it
# reports a task finished and by the orchestrator to verify that report.
#
#   bash .claude/tools/check-clean.sh
#
# Why one command for both sides: the recurring failure this exists to stop is
# a subagent reporting "done" and the orchestrator bouncing it for failing
# specs or Clean Code violations. Same script, same rules, same output closes
# that gap by construction: there is nothing the orchestrator can reject the
# work for that the subagent could not have seen first.
#
# SCOPE: by default this judges only the lines your change ADDED, measured
# against the merge-base with develop, including uncommitted AND untracked
# work - a brand-new namespace is untracked until `git add`, and missing it
# would be the exact silent pass this tool exists to prevent. You are
# accountable for the lines you wrote, not the file you happened to open.
#
# Usage:
#   check-clean.sh                 the gate - added lines only
#   check-clean.sh --fast          skip the spec run (inner loop only, NOT the gate)
#   check-clean.sh --all           whole repo, ignore the diff (baselining)
#   check-clean.sh --base <ref>    compare against <ref> instead of develop
#
# Exit: 0 = mechanical checks pass (judgment checklist still owed)
#       1 = blocking violations, or a spec failure
#       2 = the script could not run the checks (bad ref, bb missing)
#
# Rule reference, thresholds, and the judgment checklist: docs/clean-code-gate.md

set -uo pipefail
cd "$(dirname "$0")/../.." || exit 2

BASE_REF="develop"
RUN_SPECS=1
SCOPE="lines"

while [ $# -gt 0 ]; do
  case "$1" in
    --fast)  RUN_SPECS=0 ;;
    --all)   SCOPE="all" ;;
    --base)  shift; BASE_REF="${1:-}" ;;
    -h|--help) sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

command -v bb >/dev/null 2>&1 || { echo "ERROR: bb (Babashka) not on PATH" >&2; exit 2; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

blocking=0
sections_failed=""
CLJ_GLOBS=('*.clj' '*.cljc' '*.bb')

hr() { printf '%s\n' "------------------------------------------------------------"; }

# ---------------------------------------------------------------------------
# Scope: every added line, as file|line|text
# ---------------------------------------------------------------------------

: > "$WORK/added.txt"

if [ "$SCOPE" = "all" ]; then
  echo "Scope: ENTIRE REPOSITORY (baselining mode - not the gate)"
  git ls-files -- "${CLJ_GLOBS[@]}" > "$WORK/files"
  while IFS= read -r f; do
    [ -f "$f" ] && awk -v F="$f" '{ print F "|" FNR "|" $0 }' "$f" >> "$WORK/added.txt"
  done < "$WORK/files"
else
  if ! merge_base=$(git merge-base "$BASE_REF" HEAD 2>/dev/null); then
    echo "ERROR: cannot find a merge base with '$BASE_REF'." >&2
    echo "       Fetch it first, or pass --base <ref>." >&2
    exit 2
  fi

  # One diff covers both committed-on-branch and uncommitted work.
  git diff -U0 "$merge_base" -- "${CLJ_GLOBS[@]}" \
    | awk '
        /^\+\+\+ b\// { file = substr($0, 7); next }
        /^@@ / { split($3, a, ","); ln = a[1]; sub(/^\+/, "", ln); next }
        /^\+/ && !/^\+\+\+/ { print file "|" ln "|" substr($0, 2); ln++ }
      ' > "$WORK/added.txt"

  # Untracked files are invisible to `git diff`; every line of one is added.
  git ls-files --others --exclude-standard -- "${CLJ_GLOBS[@]}" > "$WORK/untracked"
  while IFS= read -r uf; do
    [ -n "$uf" ] && [ -f "$uf" ] || continue
    awk -v F="$uf" '{ print F "|" FNR "|" $0 }' "$uf" >> "$WORK/added.txt"
  done < "$WORK/untracked"

  cut -d'|' -f1 "$WORK/added.txt" | sort -u > "$WORK/files"
  echo "Scope: lines ADDED vs $BASE_REF ($(wc -l < "$WORK/files" | tr -d ' ') file(s), $(wc -l < "$WORK/added.txt" | tr -d ' ') line(s))"
fi

if [ ! -s "$WORK/files" ]; then
  echo
  echo "No Clojure changes to check."
  echo "PASS - but note the gate checked nothing. If you changed Clojure, your"
  echo "branch may not be based on $BASE_REF."
  exit 0
fi

# ---------------------------------------------------------------------------
# 1. Unit specs
# ---------------------------------------------------------------------------

hr
echo "1. Unit specs (bb spec)"
hr

# Persisted, not in the temp dir: when this fails, the log is what you read.
mkdir -p target/clean-code
SPEC_LOG="target/clean-code/spec.log"

if [ "$RUN_SPECS" -eq 0 ]; then
  echo "  SKIPPED (--fast). This is an inner-loop shortcut, not the gate."
  echo "  You may not report a task finished on a --fast run."
elif bb spec > "$SPEC_LOG" 2>&1; then
  echo "  PASS  $(grep -E 'examples?, ' "$SPEC_LOG" | tail -1 | sed 's/\x1b\[[0-9;]*m//g')"
else
  echo "  FAIL  specs failed:"
  sed 's/\x1b\[[0-9;]*m//g' "$SPEC_LOG" | grep -vE '^Downloading' | tail -30 | sed 's/^/    /'
  echo
  echo "    Full log: $SPEC_LOG"
  blocking=$((blocking + 1))
  sections_failed="$sections_failed specs"
fi

# ---------------------------------------------------------------------------
# 2. Textual smells no analyzer has a rule for
# ---------------------------------------------------------------------------

hr
echo "2. Commented-out code, deferred work, suppressions"
hr

text_fail=0

report_text_smell() {
  local label="$1" pattern="$2" advice="$3"
  local hits
  hits=$(grep -E "$pattern" "$WORK/added.txt" 2>/dev/null || true)
  if [ -n "$hits" ]; then
    echo "  FAIL  $label:"
    printf '%s\n' "$hits" | awk -F'|' '{ printf "    %s:%s\n      %s\n", $1, $2, substr($3,1,100) }'
    echo "      -> $advice"
    text_fail=1
  fi
}

# Commented-out code, detected by SHAPE: a line comment whose body opens a
# form, or a #_ reader discard in front of a form. Prose comments almost never
# start with "(".
report_text_smell \
  "commented-out code" \
  '\|[0-9]+\|([^;"]*;+[[:space:]]*\(|.*#_[[:space:]]*[(\[{])' \
  "Delete it. Version control is the record of code that used to exist."

# "We'll fix it later" markers - Clean Coder professionalism.
report_text_smell \
  "deferred-work marker" \
  '\|[0-9]+\|.*;.*(TODO|FIXME|XXX|HACK)' \
  "Do it now, or open an issue and reference it by number instead."

# Linter suppressions. "Fixed" means decomposed, not silenced.
report_text_smell \
  "new linter suppression" \
  '\|[0-9]+\|.*clj-kondo/ignore' \
  "Fix the finding instead of suppressing it (docs/clean-code-gate.md)."

if [ "$text_fail" -eq 0 ]; then
  echo "  PASS  no commented-out code, deferred-work markers, or new suppressions"
else
  blocking=$((blocking + 1))
  sections_failed="$sections_failed text-smells"
fi

# ---------------------------------------------------------------------------
# Verdict
# ---------------------------------------------------------------------------

echo
hr
if [ "$blocking" -gt 0 ]; then
  echo "RESULT: FAIL"
  hr
  echo
  echo "Blocking sections:$sections_failed"
  echo
  echo "You may NOT report this task as finished. Fix the violations above and"
  echo "rerun this exact command. Do not suppress a rule to get past it, and do"
  echo "not report the task done with a caveat - if a violation cannot be fixed,"
  echo "stop and report the blocker to the orchestrator instead."
  exit 1
fi

echo "RESULT: MECHANICAL CHECKS PASS"
hr
echo
cat <<'EOF'
The machine has checked what it can. These rules cannot be automated and are
still owed - answer every line, in the completion report, with PASS or FAIL and
one clause of evidence. "All good" is not an answer; name the file.

  [ ] SLAP        Every function I added does its work at ONE level of
                  abstraction - no function mixes orchestration (calling named
                  steps) with detail (index arithmetic, string building).
                  Evidence: name the longest function you added and its level.
  [ ] SRP         Every namespace I touched can be described in one sentence
                  with no "and". Evidence: give that sentence for each new one.
  [ ] Purity      Game rules I added are pure functions of their arguments.
                  Side effects (Quil calls, I/O, atoms) live only in veil.main
                  and veil.ui. Evidence: name the one impure fn you added, or
                  say there is none.
  [ ] Naming      Every name I introduced can be understood without reading its
                  implementation, and no name needs a comment to explain it.
  [ ] Why-not-what Every comment I added explains WHY, not WHAT.
                  Evidence: quote one comment you kept.
  [ ] Test intent Each `it` I added fails for exactly ONE reason, and its
                  description says which. Evidence: name one and the reason.
  [ ] AAA         Each `it` has visible arrange / act / assert phases, in that
                  order, with no assertion before the act.
  [ ] No new debt I introduced no code path that exists only for tests, and no
                  abstraction with a single caller added "for later".

Criteria and worked examples: docs/clean-code-gate.md
EOF
exit 0
