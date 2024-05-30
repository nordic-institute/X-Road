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

#prepate initial values

#private keys
vault kv put --mount secret alias_localhost content=@/etc/xroad-edc/certs/cs.pkcs8