#!/bin/bash

# Set registry URL, default to localhost:5555 if not provided
REGISTRY_URL=${1:-localhost:5555}

echo "Building baseline"
if ! docker buildx inspect multiarch-builder &>/dev/null; then
  docker buildx create --name multiarch-builder --driver docker-container --bootstrap --use
else
  docker buildx use multiarch-builder
fi

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
  --tag "$REGISTRY_URL"/ss-baseline-signer-runtime \
  --file Dockerfile-signer-baseline \
  --build-arg REGISTRY_URL="$REGISTRY_URL" \
  --build-context pkcs11driver=../../../../src/libs/pkcs11wrapper \
  --push \
  .