#!/usr/bin/env bash
# Generates the Flyway V1 migration from docs/schema.sql.
# Strips the authorization-query reference section (not needed in the migration),
# rewrites the header comment, and appends the required seed rows
# (root node, purge job singletons).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_SCHEMA="${V1_SOURCE_SCHEMA_PATH:-$ROOT/docs/schema.sql}"
TARGET_FILE="${V1_TARGET_PATH:-$ROOT/src/main/resources/db/migration/V1__create_schema.sql}"
TMP_FILE="$(mktemp)"

cleanup() {
  rm -f "$TMP_FILE"
}
trap cleanup EXIT

require_file() {
  local path="$1"
  [[ -f "$path" ]] || {
    echo "[generate-schema-v1] ERROR: missing required file: $path" >&2
    exit 1
  }
}

require_file "$SOURCE_SCHEMA"

mkdir -p "$(dirname "$TARGET_FILE")"

{
  printf '%s\n' "-- trawhile — V1: full schema"
  printf '%s\n' "-- Generated from docs/schema.sql by scripts/generate-schema-v1.sh"
  printf '\n'
  awk '
    NR == 1 && $0 == "-- trawhile — PostgreSQL schema" {
      next
    }
    $0 == "-- ---------------------------------------------------------------------------" {
      header = $0
      if (getline nextline) {
        if (nextline == "-- Core authorization queries (reference)") {
          exit
        }
        print header
        print nextline
        next
      }
    }
    {
      print
    }
  ' "$SOURCE_SCHEMA"
  printf '\n'
  printf '%s\n' "-- ---------------------------------------------------------------------------"
  printf '%s\n' "-- Seeds"
  printf '%s\n' "-- ---------------------------------------------------------------------------"
  printf '\n'
  printf '%s\n' "-- Root node (parent_id IS NULL identifies the unique root)"
  printf "%s\n" "INSERT INTO nodes (id, name, is_active, sort_order)"
  printf "%s\n" "VALUES ('00000000-0000-0000-0000-000000000001', 'root', TRUE, 0);"
  printf '\n'
  printf '%s\n' "-- Purge job state singletons"
  printf "%s\n" "INSERT INTO purge_jobs (job_type, status) VALUES ('activity', 'idle');"
  printf "%s\n" "INSERT INTO purge_jobs (job_type, status) VALUES ('node', 'idle');"
} > "$TMP_FILE"

if [[ -f "$TARGET_FILE" ]] && cmp -s "$TMP_FILE" "$TARGET_FILE"; then
  echo "[generate-schema-v1] unchanged $TARGET_FILE"
  exit 0
fi

mv "$TMP_FILE" "$TARGET_FILE"
echo "[generate-schema-v1] wrote $TARGET_FILE from $SOURCE_SCHEMA"
