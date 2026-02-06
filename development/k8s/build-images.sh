#!/bin/bash
source "${BASH_SOURCE%/*}/../../.scripts/base-script.sh"

REGISTRY_URL=${1:-localhost:5555}

echo "Building Security Server images (including base).."
(cd $XROAD_HOME/deployment/security-server/images && IMAGE_REGISTRY=$REGISTRY_URL ./build-images.sh --push)
