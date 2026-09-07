#!/bin/bash
# Stage build-time artifacts that live outside this Dockerfile's context.
#
# Currently mirrors development/acme2certifier/ (the canonical overlay
# consumed by Ansible + the testca Docker image) into ./build/acme2certifier
# so testca-dev consumes the same patched files instead of a duplicate copy.

set -e

if [ -z "$XROAD_HOME" ]; then
  XROAD_HOME=$(realpath "$(pwd)/../../..")
  echo "XROAD_HOME is not set. Setting it to $XROAD_HOME"
fi

rm -rf ./build
mkdir -p ./build

cp -r "$XROAD_HOME"/development/acme2certifier ./build/
