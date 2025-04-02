#!/bin/bash

REGISTRY_URL=${1:-localhost:5555}

echo "Building baseline images.."
(cd ../docker/base && ./build-base-images.sh $REGISTRY_URL)

echo "Building Security Server images.."
(cd ../../../src && ./gradlew assemble -PxroadImageRegistry=$REGISTRY_URL)

echo "Building OpenBao init-runner"
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --tag "$REGISTRY_URL"/init-runner \
  --push \
  charts/openbao-init/init-runner
