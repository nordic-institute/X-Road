#!/bin/bash

wait_for_vault() {
  echo "Waiting for Vault to start..."
  while ! grep -q "Vault server started!" /var/log/xroad/vault.log; do
    sleep 1
  done
  echo "Vault has started."
}

#init vault server in development mode
vault server -dev &> /var/log/xroad/vault.log &

wait_for_vault

#store initial values
#NB! Keys are urlencoded as values are fetched by EDC vault client through HTTP GET

#public keys
vault kv put --mount secret public-key content=@/etc/xroad-edc/certs/public-key
vault kv put --mount secret did%3Aweb%3Ass0%253A9396%23JWK2020-RSA content=@/etc/xroad-edc/certs/ss0.crt
vault kv put --mount secret did%3Aweb%3Ass1%253A9396%23JWK2020-RSA content=@/etc/xroad-edc/certs/ss1.crt
vault kv put --mount secret did%3Aweb%3Ass0%253A9396%3Ass1%23JWK2020-RSA content=@/etc/xroad-edc/certs/ss1.crt
vault kv put --mount secret did%3Aweb%3Ass1%253A9396%3Ass0%23JWK2020-RSA content=@/etc/xroad-edc/certs/ss0.crt

#private keys
vault kv put --mount secret did%3Aweb%3Ass0%253A9396-private-key content=@/etc/xroad-edc/certs/ss0.pkcs8
vault kv put --mount secret did%3Aweb%3Ass1%253A9396-private-key content=@/etc/xroad-edc/certs/ss1.pkcs8
vault kv put --mount secret did%3Aweb%3Ass0%253A9396%3Ass1-private-key content=@/etc/xroad-edc/certs/ss1.pkcs8
vault kv put --mount secret did%3Aweb%3Ass1%253A9396%3Ass0-private-key content=@/etc/xroad-edc/certs/ss0.pkcs8
