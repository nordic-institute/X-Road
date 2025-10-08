#!/bin/bash
source ./../.scripts/base-script.sh

REGISTRY_URL=${1:-localhost:5555}


echo "Building baseline images.."
(cd $XROAD_HOME/deployment/security-server/base-images && ./build-base-images.sh $REGISTRY_URL)

echo "Building Security Server images.."
(cd $XROAD_HOME/src && ./gradlew assemble -PxroadImageRegistry=$REGISTRY_URL -PbuildImages=true)

echo "Building OpenBao init-runner"
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --tag "$REGISTRY_URL"/init-runner \
  --push \
  $XROAD_HOME/deployment/security-server/k8s/charts/openbao-init/init-runner
