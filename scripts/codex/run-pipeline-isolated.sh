#!/usr/bin/env bash
# Codex isolated fast pipeline runner.
#
# This script runs Codex inside a disposable container with a disposable
# Docker-in-Docker daemon. Inside that boundary Codex may use
# danger-full-access so it can run Maven/Testcontainers directly, while the host
# Docker socket and host filesystem stay out of the runner.

set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
# shellcheck source=scripts/codex/common.sh
source "$script_dir/common.sh"

usage() {
  cat >&2 <<'EOF'
Usage:
  scripts/codex/run-pipeline-isolated.sh <task-file> [options]

Options:
  --with-guidance <critique-file>  Feed a previous verifier critique to the generator.
  --image <name>                   Runner image name (default: trawhile-codex-runner:latest).
  --no-build                       Do not build the runner image before execution.
  --keep-workspace                 Keep the temporary workspace after the run.
  --keep-containers                Keep Docker containers/network after the run.

Environment:
  CODEX_GENERATOR_MODEL           Optional model for generator runs inside the runner.
  CODEX_VERIFIER_MODEL            Optional model for verifier runs inside the runner.
  CODEX_AUTH_FILE                 Host auth file to copy into the runner (default: $CODEX_HOME/auth.json or ~/.codex/auth.json).
  CODEX_CONFIG_FILE               Host config.toml to copy into the runner (default: $CODEX_HOME/config.toml or ~/.codex/config.toml).
  CODEX_ISOLATED_GENERATOR_SANDBOX Generator sandbox inside the container (default: danger-full-access).
  CODEX_ISOLATED_VERIFIER_SANDBOX  Verifier sandbox inside the container (default: danger-full-access).

Exit codes match scripts/codex/runner/entrypoint.sh, which follows the verifier recommendation mapping.
EOF
}

if [[ $# -lt 1 || "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  if [[ $# -lt 1 ]]; then
    exit 1
  fi
  exit 0
fi

task_file=$1
shift

repo_root=$(codex_repo_root)
[[ -f "$task_file" ]] || die "task file not found: $task_file"

guidance_file=""
image_name=${CODEX_RUNNER_IMAGE:-trawhile-codex-runner:latest}
build_image=true
keep_workspace=false
keep_containers=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-guidance)
      guidance_file="${2:-}"
      [[ -n "$guidance_file" && -f "$guidance_file" ]] || die "--with-guidance requires an existing critique file path"
      shift 2
      ;;
    --image)
      image_name="${2:-}"
      [[ -n "$image_name" ]] || die "--image requires a value"
      shift 2
      ;;
    --no-build)
      build_image=false
      shift
      ;;
    --keep-workspace)
      keep_workspace=true
      shift
      ;;
    --keep-containers)
      keep_containers=true
      shift
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

command -v docker >/dev/null 2>&1 || die "docker is required for the isolated runner"

host_codex_auth=${CODEX_AUTH_FILE:-${CODEX_HOME:-$HOME/.codex}/auth.json}
[[ -f "$host_codex_auth" ]] || die "Codex auth file not found: $host_codex_auth"
host_codex_config=${CODEX_CONFIG_FILE:-${CODEX_HOME:-$HOME/.codex}/config.toml}

task_path=$(realpath "$task_file")
task_rel=${task_path#"$repo_root"/}
if [[ "$task_rel" == "$task_path" ]]; then
  die "task file must be inside the repository"
fi

guidance_rel=""
if [[ -n "$guidance_file" ]]; then
  guidance_path=$(realpath "$guidance_file")
  guidance_rel=${guidance_path#"$repo_root"/}
  if [[ "$guidance_rel" == "$guidance_path" ]]; then
    die "guidance file must be inside the repository so it can be copied into the runner workspace"
  fi
fi

workspace_parent=$(mktemp -d /tmp/trawhile-codex-isolated.XXXXXX)
workspace="$workspace_parent/workspace"
auth_staging="$workspace_parent/codex-auth"
mkdir -p "$workspace"
mkdir -p "$auth_staging"

network_name="trawhile-codex-$(date -u +%Y%m%d%H%M%S)-$$"
dind_name="$network_name-dind"
runner_name="$network_name-runner"

cleanup() {
  local status=$?
  if [[ "$keep_containers" != true ]]; then
    docker rm -f "$runner_name" >/dev/null 2>&1 || true
    docker rm -f "$dind_name" >/dev/null 2>&1 || true
    docker network rm "$network_name" >/dev/null 2>&1 || true
  else
    echo "Keeping containers/network for debugging:"
    echo "  runner:  $runner_name"
    echo "  dind:    $dind_name"
    echo "  network: $network_name"
  fi

  if [[ "$keep_workspace" != true ]]; then
    rm -rf "$workspace_parent"
  else
    echo "Keeping temporary workspace: $workspace"
  fi

  exit "$status"
}
trap cleanup EXIT

echo "Preparing disposable workspace at $workspace"
(
  cd "$repo_root"
  tar \
    --exclude='./.git' \
    --exclude='./.local/runs' \
    --exclude='./.env' \
    --exclude='./.env.*' \
    --exclude='./config/application.yml' \
    --exclude='./target' \
    --exclude='./src/main/frontend/node_modules' \
    --exclude='./.mvn/home' \
    --exclude='./.mvn/repository' \
    -cf - .
) | tar -C "$workspace" -xf -

if [[ -n "$guidance_rel" ]]; then
  mkdir -p "$workspace/$(dirname "$guidance_rel")"
  cp "$guidance_path" "$workspace/$guidance_rel"
fi

install -m 600 "$host_codex_auth" "$auth_staging/auth.json"
if [[ -f "$host_codex_config" ]]; then
  install -m 600 "$host_codex_config" "$auth_staging/config.toml"
fi

(
  cd "$workspace"
  git init -q
  git add -A
  git -c user.name='Codex isolated runner' \
    -c user.email='codex-isolated-runner@example.invalid' \
    commit -qm 'Isolated runner baseline'
)

if [[ "$build_image" == true ]]; then
  echo "Building runner image: $image_name"
  docker build -t "$image_name" -f "$repo_root/scripts/codex/runner/Dockerfile" "$repo_root"
fi

echo "Creating isolated Docker network: $network_name"
docker network create "$network_name" >/dev/null

echo "Starting disposable Docker daemon: $dind_name"
docker run -d \
  --privileged \
  --name "$dind_name" \
  --network "$network_name" \
  --network-alias codex-docker \
  -e DOCKER_TLS_CERTDIR= \
  docker:27-dind \
  --host=tcp://0.0.0.0:2375 \
  --storage-driver=overlay2 >/dev/null

echo "Waiting for disposable Docker daemon..."
for _ in $(seq 1 60); do
  if docker exec "$dind_name" docker info >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! docker exec "$dind_name" docker info >/dev/null 2>&1; then
  die "disposable Docker daemon did not become ready"
fi

runner_args=(bash /workspace/scripts/codex/runner/entrypoint.sh "$task_rel")
if [[ -n "$guidance_rel" ]]; then
  runner_args+=(--with-guidance "$guidance_rel")
fi

echo "Running isolated Codex pipeline..."
docker_create_args=(
  create
  --name "$runner_name"
  --network "$network_name"
  -e CODEX_AUTH_FILE=/codex-auth/auth.json
  -e CODEX_HOME=/tmp/codex-home
  -e DOCKER_HOST=tcp://codex-docker:2375
  -e TESTCONTAINERS_HOST_OVERRIDE=codex-docker
  -e CODEX_GENERATOR_MODEL="${CODEX_GENERATOR_MODEL:-}"
  -e CODEX_VERIFIER_MODEL="${CODEX_VERIFIER_MODEL:-}"
  -e CODEX_ISOLATED_GENERATOR_SANDBOX="${CODEX_ISOLATED_GENERATOR_SANDBOX:-danger-full-access}"
  -e CODEX_ISOLATED_VERIFIER_SANDBOX="${CODEX_ISOLATED_VERIFIER_SANDBOX:-danger-full-access}"
)
if [[ -f "$host_codex_config" ]]; then
  docker_create_args+=(-e CODEX_CONFIG_FILE=/codex-auth/config.toml)
fi
docker_create_args+=("$image_name" "${runner_args[@]}")

docker "${docker_create_args[@]}" >/dev/null
docker cp "$workspace/." "$runner_name:/workspace"
docker cp "$auth_staging" "$runner_name:/codex-auth"

set +e
docker start -a "$runner_name"
runner_exit=$?
set -e

echo "Copying isolated run artifacts back into the host repository..."
mkdir -p "$repo_root/.local/runs"
docker cp "$runner_name:/workspace/.local/runs/." "$repo_root/.local/runs/" >/dev/null 2>&1 || true
docker cp "$runner_name:/workspace/.local/codex-isolated-last-run" "$workspace_parent/codex-isolated-last-run" >/dev/null 2>&1 || true

run_rel=""
if [[ -f "$workspace_parent/codex-isolated-last-run" ]]; then
  run_rel=$(cat "$workspace_parent/codex-isolated-last-run")
  echo "Isolated run artifacts: $repo_root/$run_rel"
  if [[ -f "$repo_root/$run_rel/diff.patch" ]]; then
    patch_file="$repo_root/$run_rel/diff.patch"
    echo "Review patch: $patch_file"
    echo "Applying isolated diff to host working tree..."
    if git -C "$repo_root" apply --check "$patch_file"; then
      git -C "$repo_root" apply "$patch_file"
      echo "Applied patch to host working tree."
    else
      echo "Error: could not apply isolated diff to host working tree." >&2
      echo "       Inspect the patch and host changes:" >&2
      echo "       git apply --check $run_rel/diff.patch" >&2
      exit 1
    fi
  fi
fi

echo
echo "=============================================="
case "$runner_exit" in
  0)
    echo " result: ACCEPT - generator's work passes verifier review"
    echo " next:   review the applied files, then commit when ready"
    echo "         git diff"
    ;;
  2)
    echo " result: RERUN WITH GUIDANCE - feed the critique back to the generator:"
    echo
    echo "         scripts/codex/run-pipeline-isolated.sh $task_rel --with-guidance $run_rel/critique.md"
    ;;
  3)
    echo " result: RERUN WITH ESCALATION - use a stronger model or adjusted settings, then rerun:"
    echo
    echo "         CODEX_GENERATOR_MODEL=<model> scripts/codex/run-pipeline-isolated.sh $task_rel --with-guidance $run_rel/critique.md"
    ;;
  4)
    echo " result: HUMAN REVIEW REQUIRED - inspect the critique and the applied files:"
    echo
    echo "         ${EDITOR:-vi} $run_rel/critique.md"
    echo "         git diff"
    ;;
  5)
    echo " result: verifier did not produce a parseable 'Recommended action:' line"
    echo " next:   inspect $run_rel/critique.md"
    ;;
  *)
    echo " result: isolated runner failed with exit code $runner_exit"
    if [[ -n "${run_rel:-}" ]]; then
      echo " next:   inspect $run_rel"
    fi
    ;;
esac
echo "=============================================="

exit "$runner_exit"
