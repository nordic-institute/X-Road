#!/bin/bash

set -e # Exit immediately if a command exits with a non-zero status.

# Ensure XROAD_HOME is set and not empty
if [ -z "$XROAD_HOME" ]; then
  echo "XROAD_HOME is not set. Exiting."
  exit 1
fi

# Build X-Road

#./gradlew clean build
#./build_packages.sh --skip-tests
# Package X-Road
#cd packages
#./build-deb.sh jammy

# Set up Central Server context and build container
cd "$XROAD_HOME"/Docker/centralserver
./init_context.sh
mkdir -p build/packages
cp "$XROAD_HOME"/src/packages/build/ubuntu22.04/* build/packages/
docker build --build-arg PACKAGE_SOURCE=internal -t xrd-centralserver-dev .

# Set up Security Server context and build container
cd "$XROAD_HOME"/Docker/securityserver
./init_context.sh
mkdir -p build/packages
cp "$XROAD_HOME"/src/packages/build/ubuntu22.04/* build/packages/
docker build --build-arg PACKAGE_SOURCE=internal -t xrd-securityserver-dev .

# Set up TestCA context and build container
cd "$XROAD_HOME"/Docker/testca
./init_context.sh
docker build -t xrd-testca .

# Set up TestCA context and build container
cd "$XROAD_HOME"/Docker/is_soap/
docker build -t xrd-is-soap .