#!/bin/bash

STATUS=$(bao status -format=json) # exits with non-zero status if not initialized or sealed

set -e

INITIALIZED=$(jq -r '.initialized' <<< $STATUS)
SEALED=$(jq -r '.sealed' <<< $STATUS)
ROOT_TOKEN_FILE="/etc/openbao/root-token"
UNSEAL_KEYS_FILE="/etc/openbao/unseal-keys"

if [ "$INITIALIZED" = "true" ]; then
  echo "OpenBao already initialized"
else
  echo "Initializing OpenBao..."
  INIT_OUTPUT=$(bao operator init -key-shares=3 -key-threshold=2 -format=json)
  jq -r '.unseal_keys_b64[]' <<< $INIT_OUTPUT >$UNSEAL_KEYS_FILE
  jq -r '.root_token' <<< $INIT_OUTPUT >$ROOT_TOKEN_FILE
  chmod 600 $ROOT_TOKEN_FILE $UNSEAL_KEYS_FILE
  chown root:root $ROOT_TOKEN_FILE $UNSEAL_KEYS_FILE
fi

if [ "$SEALED" = "false" ]; then
  echo "OpenBao already unsealed"
else
  /usr/share/xroad/scripts/secret-store-unseal.sh $UNSEAL_KEYS_FILE 2
fi


export BAO_TOKEN=$(cat $ROOT_TOKEN_FILE)

XRD_PKI_CONFIGURED=$(bao secrets list -format=json | jq 'has("xrd-pki/")')
if [ "$XRD_PKI_CONFIGURED" = "true" ]; then
  echo "X-Road secrets engine already initialized"
else
  echo "Initializing X-Road secrets engine ..."

  # Enable secrets engines
  bao secrets enable -path=xrd-pki pki || exit 1
  bao secrets enable -path=xrd-secret kv || exit 1

  # Configure PKI
  bao secrets tune -max-lease-ttl=175200h xrd-pki || exit 1
  bao write xrd-pki/root/generate/internal common_name="localhost" ttl=175200h || exit 1
  bao write xrd-pki/config/urls \
    issuing_certificates="https://127.0.0.1:8200/v1/xrd-pki/ca" \
    crl_distribution_points="https://127.0.0.1:8200/v1/xrd-pki/crl" || exit 1

  # Configure PKI tidy settings
  bao write xrd-pki/config/auto-tidy \
    tidy_cert_store=true \
    tidy_revoked_certs=true \
    safety_buffer="72h" \
    interval_duration="24h" || exit 1

  # Configure roles
  bao write xrd-pki/roles/xrd-internal \
    allow_any_name=true \
    allow_subdomains=true \
    allow_localhost=true \
    allow_ip_sans=true \
    max_ttl="87600h" || exit 1

  # Create policy for PKI and secret access
  bao policy write xroad-policy - <<EOF
path "xrd-pki/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
path "xrd-secret/*" {
  capabilities = ["read", "list"]
}
path "xrd-secret" {
  capabilities = ["list"]
}
path "sys/internal/ui/mounts/*" {
  capabilities = ["read", "list"]
}
EOF
fi


CLIENT_TOKEN_FILE="/etc/xroad/secret-store-client-token"
if [ -f $CLIENT_TOKEN_FILE ]; then
  echo "X-Road client token already exists"
else
  echo "Generating X-Road client token.."
  CLIENT_TOKEN=$(bao token create -policy=xroad-policy -format=json | jq -r '.auth.client_token')
  echo "$CLIENT_TOKEN" > $CLIENT_TOKEN_FILE
  chmod 640 $CLIENT_TOKEN_FILE
  chown xroad:xroad $CLIENT_TOKEN_FILE
fi

unset BAO_TOKEN
