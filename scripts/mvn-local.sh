#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
project_root=$(CDPATH= cd -- "$script_dir/.." && pwd)

export MAVEN_USER_HOME="${MAVEN_USER_HOME:-$project_root/.mvn/home}"

exec "$project_root/mvnw" "$@"
