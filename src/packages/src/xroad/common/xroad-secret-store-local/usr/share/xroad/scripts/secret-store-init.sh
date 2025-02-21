#!/bin/bash

STATUS=$(bao status -format=json) # exits with non-zero status if not initialized or sealed

set -e

INITIALIZED=$(jq -r '.initialized' <<< $STATUS)
SEALED=$(jq -r '.sealed' <<< $STATUS)
KEYS_FILE="/etc/openbao/secret-store-keys.json"

if [ "$INITIALIZED" = "true" ]; then
  echo "OpenBao already initialized"
else
  echo "Initializing OpenBao..."
  bao operator init -key-shares=3 -key-threshold=2 -format=json > $KEYS_FILE
  chmod 600 $KEYS_FILE
fi


if [ ! -f "$KEYS_FILE" ]; then
  echo "Keys file not found"
  exit 1
fi


if [ "$SEALED" = "false" ]; then
  echo "OpenBao already unsealed"
else
  echo "Unsealing OpenBao..."
  # Read first two keys for unsealing
  KEY1=$(jq -r '.unseal_keys_b64[0]' "$KEYS_FILE")
  KEY2=$(jq -r '.unseal_keys_b64[1]' "$KEYS_FILE")

  # Unseal with two keys
  bao operator unseal "$KEY1"
  bao operator unseal "$KEY2"
fi


export BAO_TOKEN=$(cat $KEYS_FILE | jq -r '.root_token')

XRD_PKI_CONFIGURED=$(bao secrets list -format=json | jq 'has("xrd-pki/")')
if [ "$XRD_PKI_CONFIGURED" = "true" ]; then
  echo "X-Road secrets engine already initialized"
else
  echo "Initializing X-Road secrets engine ..."

  # Enable secrets engines
  bao secrets enable -path=xrd-pki pki || exit 1
  bao secrets enable -path=xrd-secret kv || exit 1
  bao secrets enable -path=xrd-ds-secret -version=2 kv || exit 1

  # Configure PKI
  bao secrets tune -max-lease-ttl=87600h xrd-pki || exit 1
  bao write xrd-pki/root/generate/internal common_name="localhost" ttl=8760h || exit 1
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
  bao write xrd-pki/roles/xrd-rpc-internal \
    allow_any_name=true \
    allow_subdomains=true \
    allow_localhost=true \
    allow_ip_sans=true \
    max_ttl="300h" || exit 1

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
path "xrd-ds-secret/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
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
