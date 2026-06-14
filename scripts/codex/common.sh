#!/usr/bin/env bash

set -euo pipefail

codex_script_dir() {
  CDPATH= cd -- "$(dirname "${BASH_SOURCE[0]}")" && pwd
}

codex_repo_root() {
  local script_dir
  script_dir="$(codex_script_dir)"
  CDPATH= cd -- "$script_dir/../.." && pwd
}

die() {
  echo "Error: $*" >&2
  exit 1
}

read_task_role() {
  local task_file=$1
  local role
  role=$(grep -m1 '^\*\*Role:\*\*' "$task_file" | sed -E 's/^\*\*Role:\*\* +//; s/ +$//' || true)
  [[ -n "$role" ]] || die "task brief $task_file has no '**Role:**' line"
  echo "$role" | awk '{print $1}'
}

read_test_classes() {
  local task_file=$1
  grep -m1 '^\*\*Test-classes:\*\*' "$task_file" \
    | sed -E 's/^\*\*Test-classes:\*\* +//; s/[[:space:]]+//g' || true
}

role_definition_file() {
  local repo_root=$1
  local role=$2
  local role_file="$repo_root/.claude/agents/$role.md"
  [[ -f "$role_file" ]] || die "role definition not found: $role_file"
  echo "$role_file"
}

is_run_artifact_path() {
  case "$1" in
    .local/runs/*) return 0 ;;
    *) return 1 ;;
  esac
}

capture_pipeline_diff() {
  local repo_root=$1
  local base_ref=$2
  local diff_file=$3

  (
    cd "$repo_root"
    git diff "$base_ref" -- > "$diff_file"

    while IFS= read -r -d '' file; do
      if is_run_artifact_path "$file"; then
        continue
      fi

      {
        echo
        echo "--- untracked file added by generator: $file ---"
      } >> "$diff_file"
      git diff --no-index -- /dev/null "$file" >> "$diff_file" 2>/dev/null || true
    done < <(git ls-files --others --exclude-standard -z)
  )
}

has_pipeline_diff() {
  local repo_root=$1
  local base_ref=$2

  (
    cd "$repo_root"
    if ! git diff --quiet "$base_ref" --; then
      return 0
    fi

    while IFS= read -r -d '' file; do
      if ! is_run_artifact_path "$file"; then
        return 0
      fi
    done < <(git ls-files --others --exclude-standard -z)

    return 1
  )
}
