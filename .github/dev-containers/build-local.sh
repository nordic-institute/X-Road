#!/bin/bash

set -e # Exit immediately if a command exits with a non-zero status.

# Build X-Road
cd ../../src
./compile_code.sh

# Package X-Road
cd packages
./build-deb.sh jammy

# Set up Central Server context and build container
cd ../../.github/dev-containers/centralserver
rm -rf build
./init_context.sh
mkdir -p build/packages
cp ../../src/packages/* build/packages/
docker build -t centralserver .

# Set up Central Server context and build container
cd ../../.github/dev-containers/centralserver
rm -rf build
./init_context.sh
mkdir -p build/packages
cp ../../src/packages/* build/packages/
docker build -t centralserver .

# Set up TestCA context and build container
cd ../../.github/dev-containers/testca
./init_context.sh
docker build -t testca .
