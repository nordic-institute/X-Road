#!/bin/bash

# Set registry URL, default to localhost:5555 if not provided
REGISTRY_URL=${1:-localhost:5555}

echo "Preparing LICENSE.txt and 3RD-PARTY-NOTICES.txt files"
rm -rf build/
mkdir build
cp ../../../../src/LICENSE.txt build/
cp ../../../../src/3RD-PARTY-NOTICES.txt build/

echo "Building baseline"
docker buildx create --name multiarch-builder --driver docker-container --driver-opt network=host --use

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --tag "$REGISTRY_URL"/ss-baseline-runtime \
  --file Dockerfile-baseline \
  --push \
  .

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --tag "$REGISTRY_URL"/ss-baseline-ui-runtime \
  --file Dockerfile-ui-baseline \
  --build-arg REGISTRY_URL="$REGISTRY_URL" \
  --push \
  .

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --tag "$REGISTRY_URL"/ss-baseline-backup-manager-runtime \
  --file Dockerfile-backup-manager-baseline \
  --build-arg REGISTRY_URL="$REGISTRY_URL" \
  --push \
  .