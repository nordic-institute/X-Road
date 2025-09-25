#!/bin/bash

source ./../.scripts/base-script.sh

hurl --insecure \
      --variables-file $XROAD_HOME/development/hurl/scenarios/k8-ss2/vars.env \
      --file-root $XROAD_HOME/development/hurl/scenarios/k8-ss2 \
      $XROAD_HOME/development/hurl/scenarios/k8-ss2/containerized-ss2.hurl \
      --very-verbose \
      --retry 12 \
      --retry-interval 8000
