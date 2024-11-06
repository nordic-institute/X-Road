#!/bin/sh

export BAO_ADDR='http://127.0.0.1:8200'

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

# Start  server in development mode
bao server -dev \
  -dev-root-token-id="$BAO_DEV_ROOT_TOKEN_ID" \
  -dev-listen-address="${BAO_DEV_LISTEN_ADDRESS:-"0.0.0.0:8200"}" &

# Wait for OpenBao to be initialized
wait_for_server

#Preconfigure OpenBao and create PKI for grpc.
bao secrets enable pki

# Configure CA certificate
bao write pki/root/generate/internal common_name="localhost" ttl=8760h

# Configure roles
bao write pki/roles/grpc-internal \
    allowed_domains="localhost" \
    allow_subdomains=true \
    allow_localhost=true \
    allow_ip_sans=true \
    max_ttl="300h"

# keep the container running
wait
