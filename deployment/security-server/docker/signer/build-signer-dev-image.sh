#!/bin/bash

REGISTRY_URL=${1:-localhost:5555}

echo "Building dev-signer-image ..."
if ! docker buildx inspect multiarch-builder &>/dev/null; then
  docker buildx create --name multiarch-builder --driver docker-container --driver-opt network=host --bootstrap --use
else
  docker buildx use multiarch-builder
fi

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --tag "$REGISTRY_URL"/ss-signer-dev \
  --file Dockerfile \
  --build-arg REGISTRY_URL="$REGISTRY_URL" \
  --push \
  .