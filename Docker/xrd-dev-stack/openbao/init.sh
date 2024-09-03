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

echo "Storing initial secrets"

#NB! Keys are urlencoded as values are fetched by EDC vault client through HTTP GET

#public keys
bao kv put --mount secret public-key content=@/certs/ss/public-key
bao kv put --mount secret did%3Aweb%3Ass0%253A9396%23JWK2020-RSA content=@/certs/ss/ss0.crt
bao kv put --mount secret did%3Aweb%3Ass1%253A9396%23JWK2020-RSA content=@/certs/ss/ss1.crt

#private keys
bao kv put --mount secret did%3Aweb%3Ass0%253A9396-private-key content=@/certs/ss/ss0.pkcs8
bao kv put --mount secret did%3Aweb%3Ass1%253A9396-private-key content=@/certs/ss/ss1.pkcs8

bao kv put --mount secret alias_cs content=@/certs/cs/cs.pkcs8


# keep the container running
wait