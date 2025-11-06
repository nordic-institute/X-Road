#!/bin/bash
set -e

. /usr/share/xroad/scripts/_openbao.sh

BAO_ADDR=${BAO_ADDR:-https://127.0.0.1:8200}
ROOT_TOKEN_FILE="/etc/openbao/root-token"
UNSEAL_KEYS_FILE="/etc/openbao/unseal-keys"

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
if ! is_sealed; then
  echo "OpenBao is already unsealed"
else
  echo "Unsealing OpenBao..."
  while IFS= read -r key || [ -n "$key" ]; do
    if ! unseal "$BAO_ADDR" "$key"; then
      echo "Failed to unseal OpenBao" >&2
      exit 1
    fi

    if ! is_sealed; then
      echo "Successfully unsealed OpenBao"
      break
    fi
  done < "$UNSEAL_KEYS_FILE"
fi

# Configure PKI if needed
export BAO_TOKEN=$(cat "$ROOT_TOKEN_FILE")
if curl -s -k -H "X-Vault-Token: $BAO_TOKEN" "$BAO_ADDR/v1/sys/mounts" | jq -e 'has("xrd-pki/")' >/dev/null; then
  echo "PKI store already configured"
else
  echo "Configuring PKI store..."
  configure_pki "$BAO_ADDR" "$BAO_TOKEN" || {
    echo "Failed to configure PKI" >&2
    exit 1
  }
fi

# Configure KV if needed
if curl -s -k -H "X-Vault-Token: $BAO_TOKEN" "$BAO_ADDR/v1/sys/mounts" | jq -e 'has("xrd-secret/")' >/dev/null; then
  echo "KV store already configured"
else
  echo "Configuring KV store..."
  configure_kv "$BAO_ADDR" "$BAO_TOKEN" || {
    echo "Failed to configure KV store" >&2
    exit 1
  }
fi

CLIENT_TOKEN_FILE="/etc/xroad/secret-store-client-token"
if [ -f $CLIENT_TOKEN_FILE ]; then
  echo "X-Road client token already exists"
else
  echo "Generating X-Road client token.."
  CLIENT_TOKEN=$(create_token "$BAO_ADDR" "$BAO_TOKEN")
  if [ $? -ne 0 ]; then
    echo " Failed to create X-Road client token" >&2
    exit 1
  fi
  echo "$CLIENT_TOKEN" > $CLIENT_TOKEN_FILE
  chmod 640 $CLIENT_TOKEN_FILE
  chown xroad:xroad $CLIENT_TOKEN_FILE
fi

echo "OpenBao initialization completed successfully"
