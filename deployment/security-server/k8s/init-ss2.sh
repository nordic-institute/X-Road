#!/bin/bash

hurl --insecure \
      --variables-file ../../../development/hurl/scenarios/k8-ss2/vars.env \
      --file-root ../../../development/hurl/scenarios/k8-ss2 \
      ../../../development/hurl/scenarios/k8-ss2/containerized-ss2.hurl \
      --very-verbose \
      --retry 12 \
      --retry-interval 8000
