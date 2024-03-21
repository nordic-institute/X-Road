#!/bin/bash

set -e # Exit immediately if a command exits with a non-zero status.

# Ensure XROAD_HOME is set and not empty
if [ -z "$XROAD_HOME" ]; then
  echo "XROAD_HOME is not set. Exiting."
  exit 1
fi

cd "$XROAD_HOME"/src && ./gradlew :security-server:e2e-test:e2eTest --rerun-tasks  \
  -Pe2eTestCSImage=xrd-centralserver-dev \
  -Pe2eTestSSImage=xrd-securityserver-dev \
  -Pe2eTestTestCAImage=xrd-testca \
  -Pe2eTestISSOAPImage=xrd-is-soap \
  -Pe2eTestServeReport=true
