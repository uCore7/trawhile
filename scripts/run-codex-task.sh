#!/usr/bin/env bash
set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
project_root=$(CDPATH= cd -- "$script_dir/.." && pwd)

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <task-file> [codex-exec-args...]" >&2
  exit 1
fi

task_file=$1
shift

if [[ ! -f "$task_file" ]]; then
  echo "Error: task file not found: $task_file" >&2
  exit 1
fi

if ! codex login status >/dev/null 2>&1; then
  echo "Error: log in with codex login before running Codex in batch mode." >&2
  exit 1
fi

task_path=$(realpath "$task_file")
task_rel=${task_path#"$project_root"/}

{
  printf 'Execute the task described in `%s`.\n' "$task_rel"
  printf 'Treat the file contents below as the user request.\n\n'
  cat "$task_path"
} | exec codex exec \
  -C "$project_root" \
  --sandbox danger-full-access \
  "$@" \
  -
