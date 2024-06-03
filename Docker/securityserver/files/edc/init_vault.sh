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

#public keys
vault kv put --mount secret public-key content=@/etc/xroad-edc/certs/public-key
vault kv put --mount secret did%3Aweb%3Axroad-8-ss0.s3.eu-west-1.amazonaws.com content=@/etc/xroad-edc/certs/ss0.crt
vault kv put --mount secret did%3Aweb%3Axroad-8-ss1.s3.eu-west-1.amazonaws.com content=@/etc/xroad-edc/certs/ss1.crt

#private keys
vault kv put --mount secret did%3Aweb%3Axroad-8-ss0.s3.eu-west-1.amazonaws.com-private-key content=@/etc/xroad-edc/certs/ss0.pkcs8
vault kv put --mount secret did%3Aweb%3Axroad-8-ss1.s3.eu-west-1.amazonaws.com-private-key content=@/etc/xroad-edc/certs/ss1.pksc8