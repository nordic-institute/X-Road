#!/bin/bash
set -e

. /usr/share/xroad/scripts/_openbao.sh

BAO_ADDR=${BAO_ADDR:-https://127.0.0.1:8200}
ROOT_TOKEN_FILE="/etc/openbao/root-token"
UNSEAL_KEYS_FILE="/etc/openbao/unseal-keys"

if wait_until_ready; then
  echo "OpenBao is ready"
else
  echo "Timed out waiting for OpenBao service to become ready"
  exit 1
fi

# Check if already initialized
if is_initialized; then
  echo "OpenBao is already initialized"
else
  echo "Initializing OpenBao..."
  INIT_RESPONSE=$(initialize) || {
    echo "Failed to initialize OpenBao" >&2
    exit 1
  }

  # Save root token and unseal keys
  ROOT_TOKEN=$(echo "$INIT_RESPONSE" | jq -r '.root_token')
  UNSEAL_KEYS=$(echo "$INIT_RESPONSE" | jq -r '.keys_base64[]')
  echo "$UNSEAL_KEYS" > "$UNSEAL_KEYS_FILE"
  echo "$ROOT_TOKEN" > "$ROOT_TOKEN_FILE"
  chmod 600 "$ROOT_TOKEN_FILE" "$UNSEAL_KEYS_FILE"
  chown root:root "$ROOT_TOKEN_FILE" "$UNSEAL_KEYS_FILE"
fi

# Check if sealed and unseal if needed
is_sealed; sealed_rc=$?
if [ "$sealed_rc" -eq 2 ]; then
  echo "Cannot determine OpenBao seal status; aborting" >&2
  exit 1
elif [ "$sealed_rc" -ne 0 ]; then
  echo "OpenBao is already unsealed"
else
  echo "Unsealing OpenBao..."
  while IFS= read -r key || [ -n "$key" ]; do
    if ! unseal "$BAO_ADDR" "$key"; then
      echo "Failed to unseal OpenBao" >&2
      exit 1
    fi

    is_sealed; sealed_rc=$?
    if [ "$sealed_rc" -eq 2 ]; then
      echo "Cannot verify seal status after unseal; aborting" >&2
      exit 1
    elif [ "$sealed_rc" -ne 0 ]; then
      echo "Successfully unsealed OpenBao"
      break
    fi
  done < "$UNSEAL_KEYS_FILE"
fi

export BAO_TOKEN=${BAO_TOKEN:-$(cat "$ROOT_TOKEN_FILE")}

# Configure PKI if needed
if curl -s -k -H "X-Vault-Token: $BAO_TOKEN" "$BAO_ADDR/v1/sys/mounts" | jq -e 'has("xrd-pki/")' >/dev/null; then
  echo "PKI store already configured"
else
  echo "Configuring PKI store..."
  configure_pki "$BAO_ADDR" "$BAO_TOKEN" || {
    echo "Failed to configure PKI" >&2
    exit 1
  }
fi

# Configure KV stores (xrd-secret KV v1 + xrd-ds-secret KV v2). configure_kv
# is idempotent and provisions whichever mount is missing.
echo "Configuring KV stores..."
configure_kv "$BAO_ADDR" "$BAO_TOKEN" || {
  echo "Failed to configure KV store" >&2
  exit 1
}

# Seed AES encryption key for ds-* services (idempotent).
seed_ds_aes_key "$BAO_ADDR" "$BAO_TOKEN" || {
  echo "Failed to seed DS AES key" >&2
  exit 1
}

CLIENT_TOKEN_FILE="/etc/xroad/secret-store-client-token"

regenerate_client_token=true
if [ -f "$CLIENT_TOKEN_FILE" ]; then
  EXISTING_TOKEN=$(cat "$CLIENT_TOKEN_FILE")
  http_status=$(curl -s -k -o /dev/null -w "%{http_code}" \
    --connect-timeout 5 --retry 3 --retry-delay 2 \
    -H "X-Vault-Token: $EXISTING_TOKEN" \
    "$BAO_ADDR/v1/auth/token/lookup-self")
  if [ "$http_status" = "200" ]; then
    echo "X-Road client token is valid"
    regenerate_client_token=false
  else
    echo "Existing X-Road client token is invalid (HTTP $http_status), regenerating"
    rm -f "$CLIENT_TOKEN_FILE"
  fi
fi

if [ "$regenerate_client_token" = "true" ]; then
  echo "Generating X-Road client token.."
  # Use custom token ID if provided via environment variable (useful for dev/test)
  XROAD_SECRET_STORE_TOKEN_OVERRIDE="${XROAD_SECRET_STORE_TOKEN_OVERRIDE:-}"
  CLIENT_TOKEN=$(create_token "$BAO_ADDR" "$BAO_TOKEN" "xroad-policy" "0" "xroad-client" "$XROAD_SECRET_STORE_TOKEN_OVERRIDE")
  if [ $? -ne 0 ]; then
    echo " Failed to create X-Road client token" >&2
    exit 1
  fi
  echo "$CLIENT_TOKEN" > "$CLIENT_TOKEN_FILE"
  chmod 640 "$CLIENT_TOKEN_FILE"
  chown xroad:xroad "$CLIENT_TOKEN_FILE"
fi

echo "OpenBao initialization completed successfully"
