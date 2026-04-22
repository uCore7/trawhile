#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
project_root=$(CDPATH= cd -- "$script_dir/.." && pwd)

export MAVEN_USER_HOME="${MAVEN_USER_HOME:-$project_root/.mvn/home}"

should_skip_frontend=false
has_skip_npm=false
has_skip_install_nodenpm=false

for arg in "$@"; do
  case "$arg" in
    spring-boot:run)
      should_skip_frontend=true
      ;;
    test|surefire:test)
      should_skip_frontend=true
      ;;
    -Dskip.npm| -Dskip.npm=*)
      has_skip_npm=true
      ;;
    -Dskip.installnodenpm| -Dskip.installnodenpm=*)
      has_skip_install_nodenpm=true
      ;;
  esac
done

# Native backend runs pair Spring Boot with `ng serve`, and backend test runs do
# not need packaged frontend assets. Avoid the frontend install/build steps
# unless the caller explicitly overrides the skip flags.
if [ "$should_skip_frontend" = true ] && [ "$has_skip_npm" = false ]; then
  set -- -Dskip.npm=true "$@"
fi

if [ "$should_skip_frontend" = true ] && [ "$has_skip_install_nodenpm" = false ]; then
  set -- -Dskip.installnodenpm=true "$@"
fi

exec "$project_root/mvnw" "$@"
