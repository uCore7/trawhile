#!/usr/bin/env bash
# Phase 7 pipeline runner: generator → verifier → human review.
#
# Reads the **Role:** line from the task brief to dispatch to the right
# subagent and to apply role-specific tool allowlists. Captures the diff
# produced by the generator, hands it to the verifier, prints the verifier's
# structured critique, and exits with a recommendation-mapped exit code.
#
# Usage:
#   scripts/run-pipeline.sh .local/tasks/tests/E-00-C10-time-format.md
#   scripts/run-pipeline.sh .local/tasks/impl/E-00-C10-time-format.md
#
# Exit codes:
#   0 — generator completed AND verifier returned ACCEPT
#   1 — generator failed
#   2 — verifier returned RERUN WITH GUIDANCE
#   3 — verifier returned RERUN WITH ESCALATION
#   4 — verifier returned HUMAN REVIEW REQUIRED
#   5 — verifier output could not be parsed for a recommendation

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <task-file>" >&2
  exit 1
fi

TASK_FILE="$1"

if [[ ! -f "$TASK_FILE" ]]; then
  echo "Error: task file not found: $TASK_FILE" >&2
  exit 1
fi

ROLE=$(grep -m1 '^\*\*Role:\*\*' "$TASK_FILE" | sed -E 's/^\*\*Role:\*\* +//; s/ +$//' || true)
if [[ -z "$ROLE" ]]; then
  echo "Error: task brief $TASK_FILE has no '**Role:**' line" >&2
  exit 1
fi
# Brief may list multiple choices like "impl-backend | impl-frontend"; take the first.
ROLE=$(echo "$ROLE" | awk '{print $1}')

case "$ROLE" in
  test-writer)
    ALLOWED_TOOLS='Read(**),Grep(**),Glob(**),Edit(src/test/**),Write(src/test/**),Bash(./scripts/mvn-local.sh test:*),Bash(./scripts/mvn-local.sh test-compile)'
    ;;
  impl-backend)
    ALLOWED_TOOLS='Read(**),Grep(**),Glob(**),Edit(src/main/java/**),Write(src/main/java/**),Bash(./scripts/mvn-local.sh:*)'
    ;;
  impl-frontend)
    ALLOWED_TOOLS='Read(**),Grep(**),Glob(**),Edit(src/main/frontend/src/**),Write(src/main/frontend/src/**),Bash(npm:*),Bash(npx:*)'
    ;;
  *)
    echo "Error: unsupported role '$ROLE' in $TASK_FILE (expected: test-writer | impl-backend | impl-frontend)" >&2
    exit 1
    ;;
esac

VERIFIER_ALLOWED_TOOLS='Read(**),Grep(**),Glob(**)'

DIFF_FILE="$(mktemp -t pipeline-diff.XXXXXX.patch)"
CRITIQUE_FILE="$(mktemp -t pipeline-critique.XXXXXX.md)"
trap 'rm -f "$DIFF_FILE" "$CRITIQUE_FILE"' EXIT

echo "=============================================="
echo " trawhile pipeline: $TASK_FILE"
echo " role: $ROLE"
echo "=============================================="
echo

# Capture pre-run state so we can diff just this generator's changes.
PRE_RUN_REF=$(git rev-parse HEAD)
PRE_RUN_DIRTY=$(git status --porcelain)
if [[ -n "$PRE_RUN_DIRTY" ]]; then
  echo "Warning: working tree is dirty before run; diff will include uncommitted changes." >&2
fi

# Stage 1: generator
echo "[1/3] running generator subagent ($ROLE)..."
claude -p "Run the $ROLE subagent against the task brief at $TASK_FILE. Read the brief and execute exactly as instructed by the role definition in .claude/agents/$ROLE.md." \
  --allowedTools "$ALLOWED_TOOLS" \
  --permission-mode acceptEdits

echo
echo "[2/3] capturing generator diff..."
# Diff includes both staged and unstaged changes since PRE_RUN_REF, plus any new untracked files.
git diff "$PRE_RUN_REF" -- > "$DIFF_FILE"
git ls-files --others --exclude-standard >> "$DIFF_FILE" 2>/dev/null || true

if [[ ! -s "$DIFF_FILE" ]]; then
  echo "Error: generator produced no diff. Aborting before verifier." >&2
  exit 1
fi

# Stage 2: verifier
echo "[3/3] running verifier subagent..."
claude -p "Run the verifier subagent. The task brief is at $TASK_FILE. The diff produced by the generator is at $DIFF_FILE. Output the structured critique in the exact format defined in .claude/agents/verifier.md. End your output with a single line of the form 'Recommended action: <ACCEPT | RERUN WITH GUIDANCE | RERUN WITH ESCALATION | HUMAN REVIEW REQUIRED>' so this script can capture the recommendation." \
  --allowedTools "$VERIFIER_ALLOWED_TOOLS" \
  --permission-mode default \
  | tee "$CRITIQUE_FILE"

echo
echo "=============================================="
echo " verifier critique saved to $CRITIQUE_FILE"
echo "=============================================="

RECOMMENDATION=$(grep -i '^Recommended action:' "$CRITIQUE_FILE" | tail -n1 | sed -E 's/^Recommended action: *//I; s/ *$//')

case "$RECOMMENDATION" in
  ACCEPT)
    echo " result: ACCEPT — generator's work passes verifier review"
    exit 0
    ;;
  "RERUN WITH GUIDANCE")
    echo " result: RERUN WITH GUIDANCE — feed the critique back to the generator and re-run"
    exit 2
    ;;
  "RERUN WITH ESCALATION")
    echo " result: RERUN WITH ESCALATION — bump model tier and re-run"
    exit 3
    ;;
  "HUMAN REVIEW REQUIRED")
    echo " result: HUMAN REVIEW REQUIRED — manual judgement needed on the critique above"
    exit 4
    ;;
  *)
    echo " result: verifier did not produce a parseable 'Recommended action:' line"
    exit 5
    ;;
esac
