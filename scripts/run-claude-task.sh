#!/usr/bin/env bash
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

claude -p "$(cat "$TASK_FILE")" \
  --allowedTools "Read,Write,Edit,Bash" \
  --permission-mode acceptEdits
