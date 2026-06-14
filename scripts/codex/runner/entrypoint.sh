#!/usr/bin/env bash
# Runs inside the isolated Codex runner container.

set -euo pipefail

repo_root=/workspace
script_dir="$repo_root/scripts/codex"

# shellcheck source=scripts/codex/common.sh
source "$script_dir/common.sh"

usage() {
  cat >&2 <<'EOF'
Usage:
  scripts/codex/runner/entrypoint.sh <task-file> [--with-guidance <critique-file>]
EOF
}

if [[ $# -lt 1 || "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  if [[ $# -lt 1 ]]; then
    exit 1
  fi
  exit 0
fi

task_file=$1
shift

guidance_file=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-guidance)
      guidance_file="${2:-}"
      [[ -n "$guidance_file" ]] || die "--with-guidance requires a file path"
      shift 2
      ;;
    *)
      die "unknown argument: $1"
      ;;
  esac
done

cd "$repo_root"
git config --global --add safe.directory "$repo_root"

[[ -f "$task_file" ]] || die "task file not found inside runner: $task_file"
if [[ -n "$guidance_file" ]]; then
  [[ -f "$guidance_file" ]] || die "guidance file not found inside runner: $guidance_file"
fi

install -d -m 700 "${CODEX_HOME:-/tmp/codex-home}"
if [[ -n "${CODEX_AUTH_FILE:-}" ]]; then
  install -m 600 "$CODEX_AUTH_FILE" "${CODEX_HOME:-/tmp/codex-home}/auth.json"
fi
if [[ -n "${CODEX_CONFIG_FILE:-}" ]]; then
  install -m 600 "$CODEX_CONFIG_FILE" "${CODEX_HOME:-/tmp/codex-home}/config.toml"
fi

if ! codex login status >/dev/null 2>&1; then
  die "Codex is not logged in inside the isolated runner"
fi

role=$(read_task_role "$task_file")
role_file=$(role_definition_file "$repo_root" "$role")

case "$role" in
  test-writer|impl-backend|impl-frontend) ;;
  *) die "unsupported role '$role' in $task_file (expected: test-writer | impl-backend | impl-frontend)" ;;
esac

test_classes=""
if [[ "$role" == "test-writer" || "$role" == "impl-backend" ]]; then
  test_classes=$(read_test_classes "$task_file")
  [[ -n "$test_classes" ]] || die "task brief $task_file has no '**Test-classes:**' line"
fi

task_basename=$(basename "$task_file" .md)
timestamp=${CODEX_RUN_TIMESTAMP:-$(date -u +%Y%m%dT%H%M%SZ)}
run_dir="$repo_root/.local/runs/$task_basename/$timestamp"
mkdir -p "$run_dir"
echo ".local/runs/$task_basename/$timestamp" > "$repo_root/.local/codex-isolated-last-run"

diff_file="$run_dir/diff.patch"
critique_file="$run_dir/critique.md"
generator_out="$run_dir/generator.md"
mvn_log=""

pre_run_ref=$(git rev-parse HEAD)
pre_run_dirty=$(git status --porcelain)
if [[ -n "$pre_run_dirty" ]]; then
  echo "Warning: isolated runner workspace was dirty before generator run." >&2
fi

echo "=============================================="
echo " trawhile Codex isolated pipeline"
echo " brief:        $task_file"
echo " role:         $role"
echo " run dir:      $run_dir"
echo " docker host:  ${DOCKER_HOST:-unset}"
echo " generator:    sandbox=${CODEX_ISOLATED_GENERATOR_SANDBOX:-danger-full-access}"
echo " verifier:     sandbox=${CODEX_ISOLATED_VERIFIER_SANDBOX:-danger-full-access}"
if [[ -n "$guidance_file" ]]; then
  echo " guidance:     $guidance_file"
fi
echo "=============================================="
echo

generator_prompt="$run_dir/generator.prompt.md"
{
  echo "Run the $role generator for the task brief at $task_file."
  echo
  echo "You are running inside an isolated disposable Codex runner. The host script intentionally started this runner so that you may use the normal self-correction loop from the role definition."
  echo "You may run build/test/lint commands allowed by the role, including ./scripts/mvn-local.sh for backend tasks."
  echo "Docker/Testcontainers access points at a disposable Docker daemon via DOCKER_HOST. Do not attempt to access the host Docker socket."
  echo "Do not run git write operations such as commit, push, pull, fetch, merge, rebase, reset, stash, or branch deletion."
  echo
  if [[ -n "$guidance_file" ]]; then
    echo "--- PRIOR VERIFIER GUIDANCE ---"
    cat "$guidance_file"
    echo "--- END PRIOR VERIFIER GUIDANCE ---"
    echo
  fi
  echo "--- ROLE DEFINITION: $role_file ---"
  cat "$role_file"
  echo "--- END ROLE DEFINITION ---"
  echo
  echo "--- TASK BRIEF: $task_file ---"
  cat "$task_file"
  echo "--- END TASK BRIEF ---"
} > "$generator_prompt"

echo "[1/4] running Codex generator ($role) with full access inside isolated runner..."
generator_args=(
  exec
  -C "$repo_root"
  --sandbox "${CODEX_ISOLATED_GENERATOR_SANDBOX:-danger-full-access}"
  -c 'approval_policy="never"'
  -c 'shell_environment_policy.exclude=["CODEX_HOME","CODEX_AUTH_FILE","OPENAI_API_KEY","CODEX_API_KEY"]'
  -o "$generator_out"
)
if [[ -n "${CODEX_GENERATOR_MODEL:-}" ]]; then
  generator_args=(-m "$CODEX_GENERATOR_MODEL" "${generator_args[@]}")
fi
codex "${generator_args[@]}" - < "$generator_prompt"

rm -f "$repo_root/.claude/settings.local.json"

echo "[2/4] checking role file scope..."
"$script_dir/check-scope.sh" "$role" "$pre_run_ref"

echo "[3/4] capturing generator diff..."
capture_pipeline_diff "$repo_root" "$pre_run_ref" "$diff_file"
if ! has_pipeline_diff "$repo_root" "$pre_run_ref"; then
  echo "Error: generator produced no diff. Inspect $run_dir for context." >&2
  exit 1
fi

if [[ -n "$test_classes" ]]; then
  mvn_log="$run_dir/mvn.log"
  echo "      capturing Maven evidence to $mvn_log (classes: $test_classes)..."
  set +e
  "$repo_root/scripts/mvn-local.sh" test -Dtest="$test_classes" > "$mvn_log" 2>&1
  mvn_exit=$?
  set -e
  echo "--- mvn exit code: $mvn_exit ---" >> "$mvn_log"
  echo "      Maven exit code: $mvn_exit (recorded as verifier evidence)"
fi

echo "[4/4] running Codex verifier..."
diff_before_verifier="$run_dir/diff-before-verifier.patch"
capture_pipeline_diff "$repo_root" "$pre_run_ref" "$diff_before_verifier"

verifier_prompt="$run_dir/verifier.prompt.md"
{
  echo "Run the verifier role against this generator output."
  echo "The task brief is at $task_file."
  echo "The generator diff is at $diff_file."
  if [[ -n "$mvn_log" && -f "$mvn_log" ]]; then
    echo "The captured Maven stdout+stderr is at $mvn_log. Read it for empirical compile-and-test evidence."
  fi
  echo
  echo "You are running in an isolated verifier context. You may inspect files with read-only shell commands such as sed, nl, and rg, but do not modify files and do not run tests."
  echo "Output the structured critique in the exact format defined below."
  echo "End your output with a single line of the form:"
  echo "Recommended action: <ACCEPT | RERUN WITH GUIDANCE | RERUN WITH ESCALATION | HUMAN REVIEW REQUIRED>"
  echo
  echo "--- VERIFIER ROLE DEFINITION ---"
  cat "$repo_root/.claude/agents/verifier.md"
  echo "--- END VERIFIER ROLE DEFINITION ---"
} > "$verifier_prompt"

verifier_args=(
  exec
  -C "$repo_root"
  --sandbox "${CODEX_ISOLATED_VERIFIER_SANDBOX:-danger-full-access}"
  -c 'approval_policy="never"'
  -c 'shell_environment_policy.exclude=["CODEX_HOME","CODEX_AUTH_FILE","OPENAI_API_KEY","CODEX_API_KEY"]'
  -o "$critique_file"
)
if [[ -n "${CODEX_VERIFIER_MODEL:-}" ]]; then
  verifier_args=(-m "$CODEX_VERIFIER_MODEL" "${verifier_args[@]}")
fi
codex "${verifier_args[@]}" - < "$verifier_prompt"

diff_after_verifier="$run_dir/diff-after-verifier.patch"
capture_pipeline_diff "$repo_root" "$pre_run_ref" "$diff_after_verifier"
if ! cmp -s "$diff_before_verifier" "$diff_after_verifier"; then
  echo "Error: verifier modified the workspace. Inspect $diff_before_verifier and $diff_after_verifier." >&2
  exit 1
fi

echo
echo "=============================================="
echo " verifier critique saved to: $critique_file"
echo " full run artefacts:        $run_dir"
echo "=============================================="

recommendation=$(grep -i '^Recommended action:' "$critique_file" | tail -n1 | sed -E 's/^Recommended action: *//I; s/ *$//')

case "$recommendation" in
  ACCEPT)
    echo " result: ACCEPT - generator's work passes verifier review"
    exit 0
    ;;
  "RERUN WITH GUIDANCE")
    echo " result: RERUN WITH GUIDANCE - feed the critique back to the generator:"
    echo
    echo "   ./scripts/codex/run-pipeline-isolated.sh $task_file --with-guidance .local/runs/$task_basename/$timestamp/critique.md"
    echo
    exit 2
    ;;
  "RERUN WITH ESCALATION")
    echo " result: RERUN WITH ESCALATION - bump model tier and re-run"
    exit 3
    ;;
  "HUMAN REVIEW REQUIRED")
    echo " result: HUMAN REVIEW REQUIRED - manual judgement needed on the critique above"
    exit 4
    ;;
  *)
    echo " result: verifier did not produce a parseable 'Recommended action:' line"
    exit 5
    ;;
esac
