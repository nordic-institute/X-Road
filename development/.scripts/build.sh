#!/bin/bash -e
set -e

SCRIPT_DIR=$(dirname "$(realpath "${BASH_SOURCE[0]}")")
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
origin="$(pwd)"

source "${ROOT_DIR}/scripts/lib/base-script.sh"

SERVICE_CONFIG_CSV="${ROOT_DIR}/scripts/lib/service-config.csv"
gradleArgs="clean build -x check "

gradleModule="$(resolve_module_gradle_path "$1" "${SERVICE_CONFIG_CSV}")" || {
  echo "Unknown module: $1" >&2
  exit 1
}

cd "$SCRIPT_DIR"/../../src/
set -o xtrace
./gradlew $gradleArgs -p $gradleModule
set +o xtrace
cd "$origin"
