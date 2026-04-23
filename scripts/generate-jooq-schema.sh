#!/usr/bin/env bash
# Generates the jOOQ DDLDatabase input from docs/schema.sql.
# This file is a build-only subset of the canonical schema and must not become
# an independently maintained schema source.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SOURCE_SCHEMA="${JOOQ_SOURCE_SCHEMA_PATH:-$ROOT/docs/schema.sql}"
TARGET_FILE="${JOOQ_TARGET_PATH:-$ROOT/src/main/resources/db/codegen/jooq-schema.sql}"
TMP_FILE="$(mktemp)"

cleanup() {
  rm -f "$TMP_FILE"
}
trap cleanup EXIT

require_file() {
  local path="$1"
  [[ -f "$path" ]] || {
    echo "[generate-jooq-schema] ERROR: missing required file: $path" >&2
    exit 1
  }
}

extract_statement() {
  local start_line="$1"
  awk -v start="$start_line" '
    index($0, start) == 1 {
      in_block = 1
    }
    in_block {
      print
    }
    in_block && /;[[:space:]]*$/ {
      exit
    }
  ' "$SOURCE_SCHEMA"
}

require_file "$SOURCE_SCHEMA"

mkdir -p "$(dirname "$TARGET_FILE")"

{
  printf '%s\n' "-- Build-only schema subset for jOOQ code generation."
  printf '%s\n' "-- Generated from docs/schema.sql by scripts/generate-jooq-schema.sh."
  printf '%s\n' "-- Runtime schema remains the canonical docs/schema.sql and derived Flyway migration."
  printf '%s\n' "-- Runtime PostgreSQL functions are intentionally omitted here because the OSS jOOQ DDLDatabase parser cannot parse CREATE FUNCTION."
  printf '\n'
  extract_statement "CREATE TABLE nodes ("
  printf '\n'
  extract_statement "CREATE TABLE users ("
  printf '\n'
  extract_statement "CREATE TYPE auth_level AS ENUM ('view', 'track', 'admin');"
  printf '\n'
  extract_statement "CREATE TABLE node_authorizations ("
} > "$TMP_FILE"

if [[ -f "$TARGET_FILE" ]] && cmp -s "$TMP_FILE" "$TARGET_FILE"; then
  echo "[generate-jooq-schema] unchanged $TARGET_FILE"
  exit 0
fi

mv "$TMP_FILE" "$TARGET_FILE"
echo "[generate-jooq-schema] wrote $TARGET_FILE from $SOURCE_SCHEMA"
