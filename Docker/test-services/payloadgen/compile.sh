#!/bin/bash
set -e

echo "Starting cross-compilation for payloadgen..."

# Create output directories
mkdir -p ./target

# Build the ARM64 version of the container
echo "Building ARM64 cross-compilation container..."
docker build -t payloadgen-builder-arm64 -f builder.Dockerfile .

# Build the x86_64 version of the container
echo "Building x86_64 cross-compilation container..."
docker build --platform linux/amd64 -t payloadgen-builder-x86 -f builder.Dockerfile .

# Run for x86_64 build with matching platform image
echo "Building x86_64 binary..."
docker run --rm --platform linux/amd64 \
  -v "$(pwd):/opt/payloadgen" \
  payloadgen-builder-x86 \
  cargo build --release

# Run for ARM64 build with matching platform image
echo "Building arm64 binary..."
docker run --rm \
  -v "$(pwd):/opt/payloadgen" \
  payloadgen-builder-arm64 \
  cargo build --release --target aarch64-unknown-linux-gnu

echo "Cross-compilation complete!"
