#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

CANONICAL_FILES=(
  "docs/requirements-ur.md"
  "docs/requirements-sr.md"
  "docs/glossary.md"
  "docs/schema.sql"
  "docs/architecture.md"
  "CLAUDE.md"
  "AGENTS.md"
)

GENERATED_PHASE2_FILES=(
  "spec/openapi.yaml"
  "src/main/resources/db/migration/V1__create_schema.sql"
)

MANUAL_PHASE2_FILES=(
  ".github/workflows/ci.yml"
)

RUN_COMPILE=0
COMMAND="${1:-run}"

if [[ "${COMMAND}" == "--help" || "${COMMAND}" == "-h" ]]; then
  COMMAND="help"
fi

shift "$(( $# > 0 ? 1 : 0 ))" || true

while [[ $# -gt 0 ]]; do
  case "$1" in
    --compile)
      RUN_COMPILE=1
      ;;
    --help|-h)
      COMMAND="help"
      ;;
    *)
      echo "Unknown option: $1" >&2
      COMMAND="help"
      break
      ;;
  esac
  shift
done

info() {
  printf "\n[%s] %s\n" "phase2" "$1"
}

warn() {
  printf "\n[%s] WARNING: %s\n" "phase2" "$1" >&2
}

die() {
  printf "\n[%s] ERROR: %s\n" "phase2" "$1" >&2
  exit 1
}

have_rg() {
  command -v rg >/dev/null 2>&1
}

search_files() {
  local pattern="$1"
  shift
  if have_rg; then
    rg -n "$pattern" "$@"
  else
    grep -R -nE "$pattern" "$@"
  fi
}

check_files_exist() {
  local missing=0
  local path
  info "Checking Phase 1 inputs and Phase 2 outputs"
  for path in "${CANONICAL_FILES[@]}" "${GENERATED_PHASE2_FILES[@]}" "${MANUAL_PHASE2_FILES[@]}"; do
    if [[ ! -f "${ROOT}/${path}" ]]; then
      printf "  missing  %s\n" "$path" >&2
      missing=1
    else
      printf "  ok       %s\n" "$path"
    fi
  done
  [[ $missing -eq 0 ]] || die "Required Phase 2 files are missing"
}

run_optional_generator() {
  local label="$1"
  local env_name="$2"
  local default_cmd="$3"
  local cmd="${!env_name:-$default_cmd}"
  if [[ -z "$cmd" ]]; then
    printf "  skip     %s (set %s to enable)\n" "$label" "$env_name"
    return 0
  fi
  printf "  run      %s\n" "$label"
  (cd "$ROOT" && bash -lc "$cmd")
}

run_generators() {
  info "Running configured Phase 2 generators"
  run_optional_generator \
    "Flyway V1 generator" \
    "PHASE2_GENERATE_V1_CMD" \
    "./scripts/generate-schema-v1.sh"
}

check_no_stale_terms() {
  local findings
  info "Checking for stale terminology in docs, tasks, and source comments"
  if findings="$(
    cd "$ROOT" && search_files \
      "time_entries|TimeEntry|getTimeEntries|OAuth2 provider|successful OAuth2 login|failed OAuth2 login|/sse/events" \
      docs spec tasks README.md CLAUDE.md src/main/java 2>/dev/null
  )"; then
    if [[ -n "$findings" ]]; then
      printf "%s\n" "$findings" >&2
      die "Stale Phase 2 terminology found"
    fi
  fi
  printf "  ok       no targeted stale terminology found\n"
}

check_contract_markers() {
  info "Checking key Phase 2 contract markers"

  grep -q "CREATE TABLE time_records" "$ROOT/docs/schema.sql" \
    || die "docs/schema.sql does not define time_records"
  grep -q "CREATE TABLE time_records" "$ROOT/src/main/resources/db/migration/V1__create_schema.sql" \
    || die "V1 migration does not define time_records"

  grep -q "/time-records:" "$ROOT/spec/openapi.yaml" \
    || die "spec/openapi.yaml is missing /time-records"
  grep -q "/events:" "$ROOT/spec/openapi.yaml" \
    || die "spec/openapi.yaml is missing /events"
  grep -q "get_time_records" "$ROOT/docs/requirements-sr.md" \
    || die "docs/requirements-sr.md is missing get_time_records"
  grep -q "purge-cron" "$ROOT/docs/requirements-sr.md" \
    || die "docs/requirements-sr.md is missing purge-cron"

  grep -q "INSERT INTO nodes (id, name, is_active, sort_order)" \
    "$ROOT/src/main/resources/db/migration/V1__create_schema.sql" \
    || die "V1 migration is missing the root-node seed"
  grep -q "INSERT INTO purge_jobs (job_type, status) VALUES ('activity', 'idle');" \
    "$ROOT/src/main/resources/db/migration/V1__create_schema.sql" \
    || die "V1 migration is missing the activity purge seed"
  grep -q "INSERT INTO purge_jobs (job_type, status) VALUES ('node', 'idle');" \
    "$ROOT/src/main/resources/db/migration/V1__create_schema.sql" \
    || die "V1 migration is missing the node purge seed"

  printf "  ok       schema, contract, and migration markers present\n"
}

compile_if_requested() {
  if [[ "$RUN_COMPILE" -ne 1 ]]; then
    return 0
  fi
  info "Running compile check"
  (cd "$ROOT" && ./scripts/mvn-local.sh --batch-mode -q -DskipTests compile)
  printf "  ok       ./scripts/mvn-local.sh -DskipTests compile\n"
}

print_usage() {
  cat <<'EOF'
Usage:
  ./scripts/phase2.sh run [--compile]
  ./scripts/phase2.sh check [--compile]
  ./scripts/phase2.sh help

Purpose:
  Orchestrate Phase 2 from docs/process.md.

Behavior:
  - `check` validates the current Phase 1/Phase 2 baseline.
  - `run` executes any configured generators, then runs the same checks.
  - `--compile` adds `./scripts/mvn-local.sh -DskipTests compile` at the end.

Optional generator hooks:
  PHASE2_GENERATE_V1_CMD

Example:
  PHASE2_GENERATE_V1_CMD="./scripts/generate-schema-v1.sh" \
  ./scripts/phase2.sh run --compile

Notes:
  - `docs/architecture.md` is treated as a canonical Phase 1 input.
  - `spec/openapi.yaml` is a canonical Phase 2 artifact edited directly (not generated by script).
  - `CLAUDE.md` and `AGENTS.md` are treated as canonical Phase 1 inputs.
  - `.github/workflows/ci.yml` is treated as a required manual Phase 2 artifact.
EOF
}

case "$COMMAND" in
  run)
    check_files_exist
    run_generators
    check_no_stale_terms
    check_contract_markers
    compile_if_requested
    info "Phase 2 run finished successfully"
    ;;
  check)
    check_files_exist
    check_no_stale_terms
    check_contract_markers
    compile_if_requested
    info "Phase 2 checks finished successfully"
    ;;
  help)
    print_usage
    ;;
  *)
    die "Unknown command: $COMMAND"
    ;;
esac
