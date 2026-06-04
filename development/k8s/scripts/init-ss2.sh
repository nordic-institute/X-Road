#!/bin/bash
# Bootstrap Security Server 2 (k8s-hosted) via the shared hurl scenario.
# Assumes external CS + CA + SS0 (native-lxd-stack / docker) are already running.

source "${BASH_SOURCE%/*}/../../../.scripts/base-script.sh"

hurl --insecure \
  --variables-file "$XROAD_HOME/development/hurl/scenarios/k8-ss2/vars.env" \
  --file-root "$XROAD_HOME/development/hurl/scenarios/k8-ss2" \
  "$XROAD_HOME/development/hurl/scenarios/k8-ss2/00-seed-dsp-ss2.hurl" \
  "$XROAD_HOME/development/hurl/scenarios/k8-ss2/containerized-ss2.hurl" \
  --very-verbose \
  --retry 12 \
  --retry-interval 8000
