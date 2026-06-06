#!/usr/bin/env bash
# Phase 7 pipeline runner: generator → verifier → human review.
#
# Reads the **Role:** line from the task brief to dispatch to the right
# subagent and to apply role-specific tool allowlists. Captures the diff
# produced by the generator, hands it to the verifier, prints the verifier's
# structured critique, and exits with a recommendation-mapped exit code.
#
# Run artefacts (diff + critique) are persisted under .local/runs/<brief-basename>/<timestamp>/
# so you can reference them in a follow-up guided rerun without scraping
# terminal scrollback.
#
# Usage:
#   scripts/run-pipeline.sh <task-file>
#       Fresh run: generator → verifier.
#
#   scripts/run-pipeline.sh <task-file> --with-guidance <critique-file>
#       Guided rerun: generator receives <critique-file> as additional context
#       (typically the previous run's critique). Then verifier runs again.
#
# Exit codes:
#   0 — generator completed AND verifier returned ACCEPT
#   1 — generator failed or produced no diff
#   2 — verifier returned RERUN WITH GUIDANCE
#   3 — verifier returned RERUN WITH ESCALATION
#   4 — verifier returned HUMAN REVIEW REQUIRED
#   5 — verifier output could not be parsed for a recommendation

set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <task-file> [--with-guidance <critique-file>]" >&2
  exit 1
fi

TASK_FILE="$1"
shift

if [[ ! -f "$TASK_FILE" ]]; then
  echo "Error: task file not found: $TASK_FILE" >&2
  exit 1
fi

GUIDANCE_FILE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-guidance)
      GUIDANCE_FILE="${2:-}"
      if [[ -z "$GUIDANCE_FILE" || ! -f "$GUIDANCE_FILE" ]]; then
        echo "Error: --with-guidance requires an existing critique file path" >&2
        exit 1
      fi
      shift 2
      ;;
    *)
      echo "Error: unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

ROLE=$(grep -m1 '^\*\*Role:\*\*' "$TASK_FILE" | sed -E 's/^\*\*Role:\*\* +//; s/ +$//' || true)
if [[ -z "$ROLE" ]]; then
  echo "Error: task brief $TASK_FILE has no '**Role:**' line" >&2
  exit 1
fi
ROLE=$(echo "$ROLE" | awk '{print $1}')   # take first if multi-choice

# `**Test-classes:** A, B, C` — required for test-writer and impl-backend so the
# pipeline knows which JUnit classes to run for the verifier's empirical mvn.log.
# Parsed early so we fail fast if missing, before spending tokens on the generator.
TEST_CLASSES=""
if [[ "$ROLE" == "test-writer" || "$ROLE" == "impl-backend" ]]; then
  TEST_CLASSES=$(grep -m1 '^\*\*Test-classes:\*\*' "$TASK_FILE" \
    | sed -E 's/^\*\*Test-classes:\*\* +//; s/[[:space:]]+//g' || true)
  if [[ -z "$TEST_CLASSES" ]]; then
    echo "Error: task brief $TASK_FILE has no '**Test-classes:**' line." >&2
    echo "  Add a single-line declaration like:" >&2
    echo "    **Test-classes:** FooIT, BarIT, BazIT" >&2
    echo "  This is required for test-writer and impl-backend briefs so the pipeline" >&2
    echo "  knows which classes to mvn-test for the verifier's empirical evidence." >&2
    exit 1
  fi
fi

case "$ROLE" in
  test-writer)
    ALLOWED_TOOLS='Read(**),Grep(**),Glob(**),Edit(src/test/**),Write(src/test/**),Bash(./scripts/mvn-local.sh test:*),Bash(./scripts/mvn-local.sh test-compile)'
    ;;
  impl-backend)
    ALLOWED_TOOLS='Read(**),Grep(**),Glob(**),Edit(src/main/java/**),Write(src/main/java/**),Edit(pom.xml),Write(pom.xml),Edit(src/main/resources/application.yml),Write(src/main/resources/application.yml),Bash(./scripts/mvn-local.sh:*)'
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

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TASK_BASENAME="$(basename "$TASK_FILE" .md)"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RUN_DIR="$REPO_ROOT/.local/runs/$TASK_BASENAME/$TIMESTAMP"
mkdir -p "$RUN_DIR"
DIFF_FILE="$RUN_DIR/diff.patch"
CRITIQUE_FILE="$RUN_DIR/critique.md"

echo "=============================================="
echo " trawhile pipeline"
echo " brief:        $TASK_FILE"
echo " role:         $ROLE"
echo " run dir:      $RUN_DIR"
if [[ -n "$GUIDANCE_FILE" ]]; then
  echo " guidance:     $GUIDANCE_FILE"
fi
echo "=============================================="
echo

PRE_RUN_REF=$(git rev-parse HEAD)
PRE_RUN_DIRTY=$(git status --porcelain)
if [[ -n "$PRE_RUN_DIRTY" ]]; then
  echo "Warning: working tree is dirty before run; diff will include uncommitted changes." >&2
fi

# Build the generator prompt; optionally prepend the prior critique as guidance.
if [[ -n "$GUIDANCE_FILE" ]]; then
  GENERATOR_PROMPT=$(cat <<EOF
Run the $ROLE subagent against the task brief at $TASK_FILE.

A prior run of this task was reviewed by the verifier and received the following critique. Address every VIOLATION in the critique before producing your output. Read the brief and execute exactly as instructed by the role definition in .claude/agents/$ROLE.md.

--- VERIFIER CRITIQUE ---

$(cat "$GUIDANCE_FILE")

--- END OF CRITIQUE ---
EOF
)
else
  GENERATOR_PROMPT="Run the $ROLE subagent against the task brief at $TASK_FILE. Read the brief and execute exactly as instructed by the role definition in .claude/agents/$ROLE.md."
fi

echo "[1/3] running generator subagent ($ROLE)..."
claude -p "$GENERATOR_PROMPT" \
  --allowedTools "$ALLOWED_TOOLS" \
  --permission-mode acceptEdits

echo
echo "[2/3] capturing generator diff to $DIFF_FILE..."
git diff "$PRE_RUN_REF" -- > "$DIFF_FILE"
{
  echo
  echo "--- untracked files added by generator ---"
  git ls-files --others --exclude-standard
} >> "$DIFF_FILE"

if ! grep -q '^diff --git' "$DIFF_FILE" && ! grep -q '^[^-]' "$DIFF_FILE"; then
  echo "Error: generator produced no diff. Inspect $RUN_DIR for context." >&2
  exit 1
fi

# Capture mvn output for test-writer / impl-backend so the verifier (which has
# no Bash tool) can read empirical compile/test results from a file artefact.
# The test classes come from the brief's `**Test-classes:**` field (parsed earlier).
MVN_LOG=""
if [[ -n "$TEST_CLASSES" ]]; then
  MVN_LOG="$RUN_DIR/mvn.log"
  echo "      capturing mvn test output to $MVN_LOG (classes: $TEST_CLASSES)..."
  set +e
  "$REPO_ROOT/scripts/mvn-local.sh" test -Dtest="$TEST_CLASSES" > "$MVN_LOG" 2>&1
  MVN_EXIT=$?
  set -e
  echo "      mvn exit code: $MVN_EXIT (recorded as artefact; not propagated)"
fi

echo "[3/3] running verifier subagent..."
VERIFIER_PROMPT="Run the verifier subagent. The task brief is at $TASK_FILE. The diff produced by the generator is at $DIFF_FILE."
if [[ -n "$MVN_LOG" && -f "$MVN_LOG" ]]; then
  VERIFIER_PROMPT="$VERIFIER_PROMPT The captured mvn stdout+stderr from running the touched test classes is at $MVN_LOG; Read it for empirical compile-and-test evidence (compile errors, test failures, exception stacks). The mvn exit code is included as the last line of $MVN_LOG."
fi
VERIFIER_PROMPT="$VERIFIER_PROMPT Output the structured critique in the exact format defined in .claude/agents/verifier.md. End your output with a single line of the form 'Recommended action: <ACCEPT | RERUN WITH GUIDANCE | RERUN WITH ESCALATION | HUMAN REVIEW REQUIRED>' so this script can capture the recommendation."

# Append the mvn exit code to the log so the verifier sees it in the same file
# it reads for empirical evidence (saves a separate Read or env-var dance).
if [[ -n "$MVN_LOG" && -f "$MVN_LOG" ]]; then
  echo "--- mvn exit code: $MVN_EXIT ---" >> "$MVN_LOG"
fi

claude -p "$VERIFIER_PROMPT" \
  --allowedTools "$VERIFIER_ALLOWED_TOOLS" \
  --permission-mode default \
  | tee "$CRITIQUE_FILE"

echo
echo "=============================================="
echo " verifier critique saved to: $CRITIQUE_FILE"
echo " full run artefacts:        $RUN_DIR"
echo "=============================================="

RECOMMENDATION=$(grep -i '^Recommended action:' "$CRITIQUE_FILE" | tail -n1 | sed -E 's/^Recommended action: *//I; s/ *$//')

case "$RECOMMENDATION" in
  ACCEPT)
    echo " result: ACCEPT — generator's work passes verifier review"
    exit 0
    ;;
  "RERUN WITH GUIDANCE")
    echo " result: RERUN WITH GUIDANCE — feed the critique back to the generator:"
    echo
    echo "   ./scripts/run-pipeline.sh $TASK_FILE --with-guidance $CRITIQUE_FILE"
    echo
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
