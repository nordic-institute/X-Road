#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"

source "${ROOT_DIR}/scripts/lib/base-script.sh"

SERVICE_CONFIG_CSV="${ROOT_DIR}/scripts/lib/service-config.csv"

usage="
./dev.sh [-m <string>] [-b] [-d] [-p] [-h]

This script builds and/or deploys given module to docker container. By default all tests are disabled.

Options:

-m => Module. proxy | proxy-ui-api
-b => Build
-d => Deploy (JAR replacement - fast, restarts service)
-p => Deploy via package install (rebuild and reinstall full package)
-h => Help

Examples:

./dev.sh -bdm proxy => Builds proxy.jar and deploys (moves) it to container and restart processes
./dev.sh -bpm proxy => Builds and reinstalls xroad-proxy package via apt/dnf
"

BUILD=false
DEPLOY=false
PACKAGE_DEPLOY=false
INVENTORY_PATH="${SCRIPT_DIR}/config/ansible_hosts.txt"

while getopts ":m:bdphi:" opt; do
  case $opt in
    b) BUILD=true ;;
    d) DEPLOY=true ;;
    p) PACKAGE_DEPLOY=true ;;
    m) MODULE=${OPTARG} ;;
    i)
        INVENTORY_PATH=${OPTARG}
        if [ ! -f "$INVENTORY_PATH" ]; then
          log_error "Inventory file not found: $INVENTORY_PATH"
          exit 1
        fi
        ;;
    h)
       printf "$usage"
       exit 0
       ;;
    *)
      printf "$usage"
      exit
      ;;
  esac
done
shift $((OPTIND -1))

if [ "$BUILD" = true ] ; then
  echo "Building module $MODULE"
  gradleModule="$(resolve_module_gradle_path "$MODULE" "${SERVICE_CONFIG_CSV}")" || {
    echo "Unknown module: $MODULE" >&2
    exit 1
  }
  origin="$(pwd)"
  cd "${ROOT_DIR}/src/"
  set -o xtrace
  ./gradlew clean build -x check -p "$gradleModule"
  set +o xtrace
  cd "$origin"
fi

if [ "$DEPLOY" = true ] ; then
  echo "Deploying module $MODULE using inventory $INVENTORY_PATH (JAR replacement)"
  source "${SCRIPT_DIR}/scripts/deploy-module.sh" "$(realpath "$INVENTORY_PATH")" $MODULE
fi

if [ "$PACKAGE_DEPLOY" = true ] ; then
  echo "Deploying module $MODULE using inventory $INVENTORY_PATH (package install)"
  source "${SCRIPT_DIR}/scripts/deploy-package.sh" "$(realpath "$INVENTORY_PATH")" $MODULE
fi
