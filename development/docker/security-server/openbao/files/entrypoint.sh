#!/bin/sh

check_server_status() {
  bao status --format=json | grep '"initialized": true' > /dev/null 2>&1
}

wait_for_server() {
  echo "Waiting for OpenBao to be initialized..."
  while ! check_server_status; do
    echo "Server not initialized yet. Checking again..."
    sleep 1
  done
  echo "OpenBao has been initialized."
}

bao server -dev \
  -dev-root-token-id="$BAO_DEV_ROOT_TOKEN_ID" \
  -dev-listen-address="${BAO_DEV_LISTEN_ADDRESS:-"0.0.0.0:8200"}" &

wait_for_server

./usr/share/xroad/scripts/secret-store-init.sh

wait