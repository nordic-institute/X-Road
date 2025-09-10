#!/bin/bash

set -e # Exit immediately if a command exits with a non-zero status.

# Ensure XROAD_HOME is set and not empty
if [ -z "$XROAD_HOME" ]; then
  XROAD_HOME=$(realpath "$(pwd)/../..")
  echo "XROAD_HOME is not set. Setting it to $XROAD_HOME"
fi

ADDITIONAL_GRADLE_ARGS=""
for i in "$@"; do
  case "$i" in
  "--use-custom-env")
    ADDITIONAL_GRADLE_ARGS="-Pe2eTestUseCustomEnv=true"
    ;;
  esac
done

if [[ $# -eq 0 ]]; then
  echo "Available args:"
  echo "--use-custom-env: Use custom environment (usually already running dev) for e2e tests"
fi

cd "$XROAD_HOME"/src && ./gradlew :security-server:e2e-test:e2eTest --rerun-tasks  \
  -Pe2eTestCSImage=xrd-centralserver-dev \
  -Pe2eTestSSImage=xrd-securityserver-dev \
  -Pe2eTestTestCAImage=xrd-testca \
  $ADDITIONAL_GRADLE_ARGS
