#!/usr/bin/env bash
# Generates the Flyway V1 migration from spec/schema.sql.
# Copies the canonical schema verbatim (after a header rewrite) and appends
# the required seed rows (root node, purge job singletons).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_SCHEMA="${V1_SOURCE_SCHEMA_PATH:-$ROOT/spec/schema.sql}"
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
  printf '%s\n' "-- Generated from spec/schema.sql by scripts/generate-schema-v1.sh"
  printf '\n'
  # Strip the source schema's leading header line ("-- trawhile — PostgreSQL schema ...");
  # the rest of the file (comments, CREATE TABLE / TYPE / FUNCTION / INDEX statements,
  # CHECK constraints, helper functions) is copied verbatim so the runtime database
  # matches spec/schema.sql exactly.
  awk 'NR == 1 && index($0, "-- trawhile — PostgreSQL schema") == 1 { next } { print }' "$SOURCE_SCHEMA"
  printf '\n'
  printf '%s\n' "-- ============================================================================"
  printf '%s\n' "-- Seeds"
  printf '%s\n' "-- ============================================================================"
  printf '\n'
  printf '%s\n' "-- Root node (parent_id IS NULL identifies the unique root)."
  printf '%s\n' "INSERT INTO nodes (id, display_name, is_active, sort_order)"
  printf '%s\n' "VALUES ('00000000-0000-0000-0000-000000000001', 'root', TRUE, 0);"
  printf '\n'
  printf '%s\n' "-- Purge / lifecycle job state singletons (one row per job_type)."
  printf '%s\n' "INSERT INTO purge_jobs (job_type, status) VALUES ('time_record_retention', 'idle');"
  printf '%s\n' "INSERT INTO purge_jobs (job_type, status) VALUES ('node_retention', 'idle');"
  printf '%s\n' "INSERT INTO purge_jobs (job_type, status) VALUES ('invitation_expiry', 'idle');"
  printf '%s\n' "INSERT INTO purge_jobs (job_type, status) VALUES ('open_record_auto_close', 'idle');"
} > "$TMP_FILE"

if [[ -f "$TARGET_FILE" ]] && cmp -s "$TMP_FILE" "$TARGET_FILE"; then
  echo "[generate-schema-v1] unchanged $TARGET_FILE"
  exit 0
fi

mv "$TMP_FILE" "$TARGET_FILE"
echo "[generate-schema-v1] wrote $TARGET_FILE from $SOURCE_SCHEMA"
