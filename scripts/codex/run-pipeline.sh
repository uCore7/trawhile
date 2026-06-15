#!/usr/bin/env bash
# Codex Phase 7 safe-local pipeline runner.
#
# Generator runs in workspace-write without approval prompts. It does not get
# direct Docker/Testcontainers access in the normal path; this script runs the
# fixed test command on the host and feeds the resulting log into later passes.

set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
# shellcheck source=scripts/codex/common.sh
source "$script_dir/common.sh"

usage() {
  cat >&2 <<'EOF'
Usage:
  scripts/codex/run-pipeline.sh <task-file> [options]

Options:
  --with-guidance <critique-file>  Feed a previous verifier critique to the generator.
  --verifier-only <run-dir>        Re-run only the verifier against an existing run dir.
  --max-generator-passes <n>       Run up to n generator/test passes before verifier (default: 1).

Environment:
  CODEX_GENERATOR_MODEL           Optional model for generator runs.
  CODEX_VERIFIER_MODEL            Optional model for verifier runs.
  CODEX_AUTH_FILE                 Host auth file to copy into a temporary CODEX_HOME.
  CODEX_CONFIG_FILE               Host config.toml to copy into the temporary CODEX_HOME.
  CODEX_GENERATOR_SANDBOX         Generator sandbox (default: workspace-write).
  CODEX_VERIFIER_SANDBOX          Verifier sandbox (default: read-only).
  CODEX_PIPELINE_VERBOSE          Set to 1 to stream full Codex transcripts (default: quiet logs).

Exit codes:
  0 — generator completed AND verifier returned ACCEPT
  1 — generator failed, produced no diff, or scope violation
  2 — verifier returned RERUN WITH GUIDANCE
  3 — verifier returned RERUN WITH ESCALATION
  4 — verifier returned HUMAN REVIEW REQUIRED
  5 — verifier output could not be parsed for a recommendation
EOF
}

if [[ $# -lt 1 ]]; then
  usage
  exit 1
fi

task_file=""
guidance_file=""
verifier_only_dir=""
max_generator_passes=1
max_generator_passes_set=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-guidance)
      guidance_file="${2:-}"
      [[ -n "$guidance_file" && -f "$guidance_file" ]] || die "--with-guidance requires an existing critique file path"
      shift 2
      ;;
    --verifier-only)
      verifier_only_dir="${2:-}"
      [[ -n "$verifier_only_dir" ]] || die "--verifier-only requires an existing run-dir path"
      if [[ -f "$verifier_only_dir" ]]; then
        die "--verifier-only requires a run-dir, not a task file. Usage: $0 <task-file> --verifier-only <run-dir>"
      fi
      [[ -d "$verifier_only_dir" ]] || die "--verifier-only requires an existing run-dir path"
      [[ -f "$verifier_only_dir/diff.patch" ]] || die "--verifier-only run-dir is missing diff.patch: $verifier_only_dir"
      shift 2
      ;;
    --max-generator-passes)
      max_generator_passes="${2:-}"
      [[ "$max_generator_passes" =~ ^[1-9][0-9]*$ ]] || die "--max-generator-passes requires a positive integer"
      max_generator_passes_set=true
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --*)
      die "unknown argument: $1"
      ;;
    *)
      if [[ -n "$task_file" ]]; then
        die "unexpected extra positional argument: $1"
      fi
      task_file="$1"
      shift
      ;;
  esac
done

[[ -n "$task_file" ]] || die "missing task file"
[[ -f "$task_file" ]] || die "task file not found: $task_file"

if [[ -n "$verifier_only_dir" && -n "$guidance_file" ]]; then
  die "--verifier-only and --with-guidance are mutually exclusive"
fi
if [[ -n "$verifier_only_dir" && "$max_generator_passes_set" == true ]]; then
  die "--verifier-only and --max-generator-passes are mutually exclusive"
fi

repo_root=$(codex_repo_root)
task_path=$(realpath "$task_file")
task_rel=${task_path#"$repo_root"/}
role=$(read_task_role "$task_path")
role_file=$(role_definition_file "$repo_root" "$role")

case "$role" in
  test-writer|impl-backend|impl-frontend) ;;
  *) die "unsupported role '$role' in $task_file (expected: test-writer | impl-backend | impl-frontend)" ;;
esac

if [[ -z "$verifier_only_dir" && ( "$role" == "test-writer" || "$role" == "impl-backend" ) ]]; then
  test_classes=$(read_test_classes "$task_path")
  [[ -n "$test_classes" ]] || die "task brief $task_file has no '**Test-classes:**' line"
fi

task_basename=$(basename "$task_file" .md)
if [[ -n "$verifier_only_dir" ]]; then
  run_dir=$(realpath "$verifier_only_dir")
else
  timestamp=$(date -u +%Y%m%dT%H%M%SZ)
  run_dir="$repo_root/.local/runs/$task_basename/$timestamp"
  mkdir -p "$run_dir"
fi

generator_path=""
if [[ -z "$verifier_only_dir" ]]; then
  blocked_bin_dir="$run_dir/blocked-bin"
  mkdir -p "$blocked_bin_dir"
  for blocked_command in mvn mvnw npm npx ng docker java javac curl wget; do
    cat > "$blocked_bin_dir/$blocked_command" <<'EOF'
#!/usr/bin/env bash
echo "Blocked in safe-local Codex generator: build/test/network commands are run by the outer pipeline, not by Codex." >&2
exit 126
EOF
    chmod +x "$blocked_bin_dir/$blocked_command"
  done
  generator_path="$blocked_bin_dir:${PATH:-/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin}"
fi

host_codex_auth=${CODEX_AUTH_FILE:-${CODEX_HOME:-$HOME/.codex}/auth.json}
[[ -f "$host_codex_auth" ]] || die "Codex auth file not found: $host_codex_auth"
host_codex_config=${CODEX_CONFIG_FILE:-${CODEX_HOME:-$HOME/.codex}/config.toml}

pipeline_codex_home=$(mktemp -d "${TMPDIR:-/tmp}/trawhile-codex-home.XXXXXX")
cleanup_codex_home() {
  rm -rf "$pipeline_codex_home"
}
trap cleanup_codex_home EXIT

install -d -m 700 "$pipeline_codex_home"
install -m 600 "$host_codex_auth" "$pipeline_codex_home/auth.json"
if [[ -f "$host_codex_config" ]]; then
  install -m 600 "$host_codex_config" "$pipeline_codex_home/config.toml"
fi

if ! CODEX_HOME="$pipeline_codex_home" codex login status >/dev/null 2>&1; then
  die "log in with codex login before running Codex in batch mode"
fi

diff_file="$run_dir/diff.patch"
critique_file="$run_dir/critique.md"
generator_sandbox=${CODEX_GENERATOR_SANDBOX:-workspace-write}
verifier_sandbox=${CODEX_VERIFIER_SANDBOX:-read-only}

echo "=============================================="
echo " trawhile Codex safe-local pipeline"
echo " brief:        $task_rel"
echo " role:         $role"
echo " run dir:      $run_dir"
if [[ -n "$verifier_only_dir" ]]; then
  echo " mode:         verifier-only (reusing existing diff.patch / test log)"
else
  echo " passes:       $max_generator_passes"
  echo " generator:    sandbox=$generator_sandbox"
fi
echo " verifier:     sandbox=$verifier_sandbox"
if [[ -n "$guidance_file" ]]; then
  echo " guidance:     $guidance_file"
fi
echo "=============================================="
echo

last_test_log=""
last_test_exit=0

if [[ -n "$verifier_only_dir" ]]; then
  for candidate in "$run_dir"/mvn.log "$run_dir"/mvn-pass-*.log "$run_dir"/frontend-pass-*.log; do
    if [[ -f "$candidate" ]]; then
      last_test_log="$candidate"
    fi
  done
else
  pre_run_ref=$(cd "$repo_root" && git rev-parse HEAD)
  pre_run_dirty=$(cd "$repo_root" && git status --porcelain)
  pre_run_changed_files="$run_dir/pre-run-changed-files.txt"
  {
    cd "$repo_root"
    git diff --name-only "$pre_run_ref" --
    git ls-files --others --exclude-standard
  } | sort -u > "$pre_run_changed_files"

  if [[ -n "$pre_run_dirty" ]]; then
    echo "Warning: working tree is dirty before run; diffs include uncommitted changes." >&2
    echo "Warning: scope checks ignore files already dirty before Codex started; use a clean worktree for strongest enforcement." >&2
  fi

  for pass in $(seq 1 "$max_generator_passes"); do
  generator_out="$run_dir/generator-pass-$pass.md"
  prompt_file="$run_dir/generator-pass-$pass.prompt.md"

  {
    echo "Run the $role generator for the task brief at $task_rel."
    echo
    echo "This is the safe-local Codex pipeline. You run with workspace-limited permissions and no approval prompts."
    echo "Do not request escalated permissions. Do not try to access Docker, Testcontainers, local databases, or other local services directly."
    echo "Do not run Maven, Gradle, npm, test, compile, lint, or build commands yourself. The outer pipeline owns all verification commands."
    echo "The outer pipeline runs the fixed test command after each pass and feeds the log back to you."
    echo
    echo "Apply the substantive constraints from the role definition below, except that its self-correction Bash/test loop is owned by this outer pipeline."
    echo "If the role definition says to run tests, treat that as an instruction for the outer pipeline, not for your Codex session."
    echo "Keep edits minimal and within the role scope. If the brief or specifications conflict, stop and report the conflict instead of guessing."
    echo "In your final output, be concise: summarize changed files, verification status, and any problems, blockers, risks, or assumptions you noticed. Do not paste full diffs, full file contents, or the task brief."
    echo
    if [[ -n "$guidance_file" ]]; then
      echo "--- PRIOR VERIFIER GUIDANCE ---"
      cat "$guidance_file"
      echo "--- END PRIOR VERIFIER GUIDANCE ---"
      echo
    fi
    if [[ -n "$last_test_log" && -f "$last_test_log" ]]; then
      echo "--- PREVIOUS HOST-SIDE TEST LOG ---"
      echo "Path: $last_test_log"
      cat "$last_test_log"
      echo "--- END PREVIOUS HOST-SIDE TEST LOG ---"
      echo
    fi
    echo "--- ROLE DEFINITION: $role_file ---"
    cat "$role_file"
    echo "--- END ROLE DEFINITION ---"
    echo
    echo "--- TASK BRIEF: $task_rel ---"
    cat "$task_path"
    echo "--- END TASK BRIEF ---"
  } > "$prompt_file"

  echo "[pass $pass/$max_generator_passes] running Codex generator ($role)..."
  codex_args=(
    exec
    -C "$repo_root"
    --sandbox "$generator_sandbox"
    -c 'approval_policy="never"'
    -c 'shell_environment_policy.exclude=["CODEX_HOME","CODEX_AUTH_FILE","OPENAI_API_KEY","CODEX_API_KEY"]'
    -c "shell_environment_policy.set={PATH=\"$generator_path\"}"
    -o "$generator_out"
  )
  if [[ -n "${CODEX_GENERATOR_MODEL:-}" ]]; then
    codex_args=(-m "$CODEX_GENERATOR_MODEL" "${codex_args[@]}")
  fi
  generator_transcript="$run_dir/generator-pass-$pass.codex.log"
  if [[ "${CODEX_PIPELINE_VERBOSE:-0}" == "1" ]]; then
    if ! CODEX_HOME="$pipeline_codex_home" codex "${codex_args[@]}" - < "$prompt_file" 2>&1 | tee "$generator_transcript"; then
      echo "Error: Codex generator failed. Transcript: $generator_transcript" >&2
      exit 1
    fi
  else
    if ! CODEX_HOME="$pipeline_codex_home" codex "${codex_args[@]}" - < "$prompt_file" > "$generator_transcript" 2>&1; then
      echo "Error: Codex generator failed. Transcript: $generator_transcript" >&2
      tail -n 80 "$generator_transcript" >&2 || true
      exit 1
    fi
    echo "      Codex transcript: $generator_transcript"
  fi
  if [[ -s "$generator_out" ]]; then
    echo
    echo "--- generator final output ---"
    cat "$generator_out"
    echo "--- end generator final output ---"
    echo
  fi

  echo "[pass $pass/$max_generator_passes] checking role file scope..."
  "$script_dir/check-scope.sh" "$role" "$pre_run_ref" --ignore-file-list "$pre_run_changed_files"

  pass_diff_file="$run_dir/diff-pass-$pass.patch"
  capture_pipeline_diff "$repo_root" "$pre_run_ref" "$pass_diff_file"
  cp "$pass_diff_file" "$diff_file"

  if ! has_pipeline_diff "$repo_root" "$pre_run_ref"; then
    echo "Error: generator produced no diff. Inspect $run_dir for context." >&2
    exit 1
  fi

  echo "[pass $pass/$max_generator_passes] running host-side task tests..."
  set +e
  "$script_dir/run-task-tests.sh" "$role" "$task_path" "$run_dir" "$pass"
  last_test_exit=$?
  set -e

  case "$role" in
    impl-backend)
      last_test_log="$run_dir/mvn-pass-$pass.log"
      if [[ "$last_test_exit" -eq 0 ]]; then
        echo "Targeted backend tests passed after pass $pass."
        break
      fi
      ;;
    test-writer)
      last_test_log="$run_dir/mvn-pass-$pass.log"
      if [[ "$pass" -lt "$max_generator_passes" ]]; then
        echo "Test-writer Maven exit code was $last_test_exit. Continuing because another pass was requested."
      fi
      ;;
    impl-frontend)
      last_test_log="$run_dir/frontend-pass-$pass.log"
      break
      ;;
  esac

  if [[ "$pass" -eq "$max_generator_passes" ]]; then
    echo "Generator pass budget exhausted; verifier will review the current diff and latest test log."
  fi
  done
fi

echo "[verifier] running Codex verifier..."
verifier_prompt="$run_dir/verifier.prompt.md"
{
  echo "Run the verifier role against this generator output."
  echo "The task brief is at $task_rel."
  echo "The generator diff is at $diff_file."
  if [[ -n "$last_test_log" && -f "$last_test_log" ]]; then
    echo "The host-side test log is at $last_test_log. Read it for empirical compile/test evidence."
  fi
  echo
  echo "You are running in a read-only verifier context. You may inspect files with read-only shell commands such as sed, nl, and rg, but do not modify files and do not run tests."
  echo "Output the structured critique in the exact format defined below."
  echo "End your output with a single line of the form:"
  echo "Recommended action: <ACCEPT | RERUN WITH GUIDANCE | RERUN WITH ESCALATION | HUMAN REVIEW REQUIRED>"
  echo
  echo "--- VERIFIER ROLE DEFINITION ---"
  cat "$(role_definition_file "$repo_root" verifier)"
  echo "--- END VERIFIER ROLE DEFINITION ---"
} > "$verifier_prompt"

verifier_args=(
  exec
  -C "$repo_root"
  --sandbox "$verifier_sandbox"
  -c 'approval_policy="never"'
  -c 'shell_environment_policy.exclude=["CODEX_HOME","CODEX_AUTH_FILE","OPENAI_API_KEY","CODEX_API_KEY"]'
  -o "$critique_file"
)
if [[ -n "${CODEX_VERIFIER_MODEL:-}" ]]; then
  verifier_args=(-m "$CODEX_VERIFIER_MODEL" "${verifier_args[@]}")
fi
verifier_transcript="$run_dir/verifier.codex.log"
if [[ "${CODEX_PIPELINE_VERBOSE:-0}" == "1" ]]; then
  if ! CODEX_HOME="$pipeline_codex_home" codex "${verifier_args[@]}" - < "$verifier_prompt" 2>&1 | tee "$verifier_transcript"; then
    echo "Error: Codex verifier failed. Transcript: $verifier_transcript" >&2
    exit 1
  fi
else
  if ! CODEX_HOME="$pipeline_codex_home" codex "${verifier_args[@]}" - < "$verifier_prompt" > "$verifier_transcript" 2>&1; then
    echo "Error: Codex verifier failed. Transcript: $verifier_transcript" >&2
    tail -n 80 "$verifier_transcript" >&2 || true
    exit 1
  fi
  echo "      Codex transcript: $verifier_transcript"
fi

echo
echo "--- verifier critique ---"
cat "$critique_file"
echo "--- end verifier critique ---"

echo
echo "=============================================="
echo " verifier critique saved to: $critique_file"
echo " full run artefacts:        $run_dir"
echo "=============================================="

recommendation=$(grep -i '^Recommended action:' "$critique_file" | tail -n1 | sed -E 's/^Recommended action: *//I; s/ *$//')

case "$recommendation" in
  ACCEPT)
    if [[ -n "$verifier_only_dir" ]]; then
      echo " result: ACCEPT — verifier review passes"
      echo " next:   inspect $critique_file if needed"
    else
      echo " result: ACCEPT — generator's work passes verifier review"
      echo " next:   review the applied files, then commit when ready"
      echo "         git diff"
    fi
    exit 0
    ;;
  "RERUN WITH GUIDANCE")
    echo " result: RERUN WITH GUIDANCE — feed the critique back to the generator:"
    echo
    echo "         scripts/codex/run-pipeline.sh $task_rel --with-guidance $critique_file"
    echo
    exit 2
    ;;
  "RERUN WITH ESCALATION")
    echo " result: RERUN WITH ESCALATION — use a stronger model or adjusted settings, then rerun:"
    echo
    echo "         CODEX_GENERATOR_MODEL=<model> scripts/codex/run-pipeline.sh $task_rel --with-guidance $critique_file"
    exit 3
    ;;
  "HUMAN REVIEW REQUIRED")
    echo " result: HUMAN REVIEW REQUIRED — manual judgement needed on the critique above"
    echo " next:   inspect the critique and the applied files"
    echo "         ${EDITOR:-vi} $critique_file"
    echo "         git diff"
    exit 4
    ;;
  *)
    echo " result: verifier did not produce a parseable 'Recommended action:' line"
    echo " next:   inspect $critique_file"
    exit 5
    ;;
esac
