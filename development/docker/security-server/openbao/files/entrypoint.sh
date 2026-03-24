#!/bin/sh

bao server -dev \
  -dev-root-token-id="$BAO_TOKEN" \
  -dev-listen-address="${BAO_DEV_LISTEN_ADDRESS:-"0.0.0.0:8200"}" &

./usr/share/xroad/scripts/secret-store-init.sh

# Seed ds-control-plane STS client secret for DCP authentication
bao kv put xrd-secret/ds-cp-sts-client-secret value="dev-sts-secret"

wait
