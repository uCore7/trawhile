#!/usr/bin/env bash

set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
# shellcheck source=scripts/codex/common.sh
source "$script_dir/common.sh"

usage() {
  echo "Usage: $0 <role> [base-ref] [--ignore-file-list <path>]" >&2
}

if [[ $# -lt 1 || "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  if [[ $# -lt 1 ]]; then
    exit 1
  fi
  exit 0
fi

role=$1
shift
base_ref=HEAD
ignore_file_list=""

if [[ $# -gt 0 && "$1" != --* ]]; then
  base_ref=$1
  shift
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    --ignore-file-list)
      ignore_file_list="${2:-}"
      [[ -n "$ignore_file_list" && -f "$ignore_file_list" ]] || die "--ignore-file-list requires an existing file"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown argument: $1"
      ;;
  esac
done

repo_root=$(codex_repo_root)

is_ignored_preexisting_change() {
  local path=$1
  [[ -n "$ignore_file_list" ]] || return 1
  grep -Fxq -- "$path" "$ignore_file_list"
}

is_allowed() {
  local path=$1

  if is_run_artifact_path "$path"; then
    return 0
  fi

  case "$role" in
    test-writer)
      case "$path" in
        src/test/*) return 0 ;;
      esac
      ;;
    impl-backend)
      case "$path" in
        src/main/java/*|pom.xml|src/main/resources/application.yml) return 0 ;;
      esac
      ;;
    impl-frontend)
      case "$path" in
        src/main/frontend/src/*|src/app/*|src/assets/*) return 0 ;;
      esac
      ;;
    verifier)
      return 1
      ;;
    *)
      die "unsupported role '$role'"
      ;;
  esac

  return 1
}

violations=()

while IFS= read -r file; do
  [[ -n "$file" ]] || continue
  if is_ignored_preexisting_change "$file"; then
    continue
  fi
  if ! is_allowed "$file"; then
    violations+=("$file")
  fi
done < <(cd "$repo_root" && git diff --name-only "$base_ref" --)

while IFS= read -r -d '' file; do
  if is_ignored_preexisting_change "$file"; then
    continue
  fi
  if ! is_allowed "$file"; then
    violations+=("$file")
  fi
done < <(cd "$repo_root" && git ls-files --others --exclude-standard -z)

if [[ ${#violations[@]} -gt 0 ]]; then
  echo "Scope violation for role '$role':" >&2
  for file in "${violations[@]}"; do
    echo "  $file" >&2
  done
  exit 1
fi

echo "Scope check passed for role '$role'."
