#!/bin/bash

echo "Applying OpenBao configuration.."
export BAO_ADDR=https://127.0.0.1:8200
export BAO_TOKEN="$(cat /etc/xroad/secret-store-root-token)"

# Enable secrets engines
bao secrets enable -path=xrd-pki pki || exit 1
bao secrets enable -path=xrd-secret kv || exit 1
bao secrets enable -path=xrd-ds-secret -version=2 kv || exit 1

# Configure PKI
bao secrets tune -max-lease-ttl=87600h xrd-pki || exit 1
bao write xrd-pki/root/generate/internal common_name="localhost" ttl=8760h || exit 1
bao write xrd-pki/config/urls \
  issuing_certificates="$BAO_ADDR/v1/xrd-pki/ca" \
  crl_distribution_points="$BAO_ADDR/v1/xrd-pki/crl" || exit 1

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

# Create token with policy attached
XROAD_TOKEN=$(bao token create -policy=xroad-policy -format=json | jq -r '.auth.client_token')
echo "$XROAD_TOKEN" > /etc/xroad/secret-store-client-token
chmod 640 /etc/xroad/secret-store-client-token
chown xroad:xroad /etc/xroad/secret-store-client-token
unset BAO_TOKEN BAO_ADDR
