#!/usr/bin/env bash
set -eu

write_devcontainer_config() {
  tmp_config=$(mktemp "${CODEX_HOME}/config.toml.tmp.XXXXXX")
  cat /workspaces/timetracker/.devcontainer/codex-config.toml > "${tmp_config}"
  chmod 600 "${tmp_config}"
  mv "${tmp_config}" "${CODEX_HOME}/config.toml"
}

mkdir -p "${CODEX_HOME}"

if [ ! -f "${CODEX_HOME}/config.toml" ] || [ ! -r "${CODEX_HOME}/config.toml" ]; then
  write_devcontainer_config
fi

# Older devcontainer revisions forced API-only login. Remove that stale line
# from persisted config so the container can also reuse ChatGPT-managed auth.
if grep -q '^forced_login_method = "api"$' "${CODEX_HOME}/config.toml"; then
  tmp_config=$(mktemp "${CODEX_HOME}/config.toml.tmp.XXXXXX")
  grep -v '^forced_login_method = "api"$' "${CODEX_HOME}/config.toml" > "${tmp_config}"
  mv "${tmp_config}" "${CODEX_HOME}/config.toml"
  chmod 600 "${CODEX_HOME}/config.toml"
fi
