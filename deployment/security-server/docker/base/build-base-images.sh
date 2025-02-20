#!/bin/bash

echo "Building baseline"
docker buildx create --name multiarch-builder --driver docker-container --driver-opt network=host --use

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --tag localhost:5555/ss-baseline-runtime \
  --file Dockerfile-baseline \
  --push \
  .

docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --tag localhost:5555/ss-baseline-ui-runtime \
  --file Dockerfile-ui-baseline \
  --push \
  .