#!/bin/bash

# Set registry URL, default to localhost:5555 if not provided
REGISTRY_URL=${1:-localhost:5555}

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