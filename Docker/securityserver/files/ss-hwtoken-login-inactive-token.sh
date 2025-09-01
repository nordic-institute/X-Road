#!/bin/bash

INACTIVE_TOKEN=$(
  sudo -u xroad -i signer-console list-tokens | awk '/inactive/ {print $2; exit}'
)

if [[ -n "${INACTIVE_TOKEN}" ]]; then
  echo "Logging in inactive token: ${INACTIVE_TOKEN}"
  sudo -u xroad -i signer-console login-token "${INACTIVE_TOKEN}" <<< "Secret1234"
fi