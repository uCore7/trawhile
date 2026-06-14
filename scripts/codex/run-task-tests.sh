#!/usr/bin/env bash

set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
# shellcheck source=scripts/codex/common.sh
source "$script_dir/common.sh"

usage() {
  echo "Usage: $0 <role> <task-file> <run-dir> <pass-number>" >&2
}

if [[ $# -ne 4 ]]; then
  usage
  exit 1
fi

role=$1
task_file=$2
run_dir=$3
pass_number=$4
repo_root=$(codex_repo_root)

mkdir -p "$run_dir"

case "$role" in
  test-writer|impl-backend)
    test_classes=$(read_test_classes "$task_file")
    [[ -n "$test_classes" ]] || die "task brief $task_file has no '**Test-classes:**' line"

    log_file="$run_dir/mvn-pass-$pass_number.log"
    echo "Running targeted Maven tests for $role: $test_classes"
    set +e
    "$repo_root/scripts/mvn-local.sh" test -Dtest="$test_classes" > "$log_file" 2>&1
    mvn_exit=$?
    set -e
    echo "--- mvn exit code: $mvn_exit ---" >> "$log_file"
    echo "Maven log saved to: $log_file"
    exit "$mvn_exit"
    ;;
  impl-frontend)
    log_file="$run_dir/frontend-pass-$pass_number.log"
    {
      echo "No frontend test runner is wired into scripts/codex/run-task-tests.sh yet."
      echo "--- frontend test exit code: 0 ---"
    } > "$log_file"
    echo "Frontend test placeholder log saved to: $log_file"
    exit 0
    ;;
  *)
    die "unsupported role '$role'"
    ;;
esac
