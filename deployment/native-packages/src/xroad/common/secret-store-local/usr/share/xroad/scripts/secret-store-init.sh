#!/bin/bash
set -e
umask 077

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
if is_sealed; then sealed_rc=0; else sealed_rc=$?; fi
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

    if is_sealed; then sealed_rc=0; else sealed_rc=$?; fi
    if [ "$sealed_rc" -eq 2 ]; then
      echo "Cannot verify seal status after unseal; aborting" >&2
      exit 1
    elif [ "$sealed_rc" -ne 0 ]; then
      echo "Successfully unsealed OpenBao"
      break
    fi
  done < "$UNSEAL_KEYS_FILE"
fi

if [ -z "${BAO_TOKEN:-}" ] && [ -s "$ROOT_TOKEN_FILE" ]; then
  BAO_TOKEN=$(cat "$ROOT_TOKEN_FILE")
fi

if [ -n "${BAO_TOKEN:-}" ]; then
  export BAO_TOKEN

  # configure_pki writes the access policy as its last step; an existing PKI
  # store only needs the policy refreshed.
  if curl -s -k -H "X-Vault-Token: $BAO_TOKEN" "$BAO_ADDR/v1/sys/mounts" | jq -e 'has("xrd-pki/")' >/dev/null; then
    echo "PKI store already configured; writing access policy..."
    configure_policy "$BAO_ADDR" "$BAO_TOKEN" || {
      echo "Failed to write access policy" >&2
      exit 1
    }
  else
    echo "Configuring PKI store and writing access policy..."
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
else
  echo "No root token on this node; skipping mount, policy and client token issuing steps"
fi

CLIENT_TOKEN_FILE="/etc/xroad/secret-store-client-token"

# Prints the HTTP status of lookup-self for the given token. Only 200, 400 and
# 403 are conclusive; any other result is retried and then returned as is.
client_token_status() {
  local token="$1"
  local attempt=1
  local max_attempts=10
  local status
  while true; do
    status=$(curl -s -k -o /dev/null -w "%{http_code}" \
      --connect-timeout 5 \
      --max-time 10 \
      -H "X-Vault-Token: $token" \
      "$BAO_ADDR/v1/auth/token/lookup-self") || true
    case "$status" in
      200|400|403) break ;;
    esac
    if [ "$attempt" -ge "$max_attempts" ]; then
      break
    fi
    attempt=$((attempt + 1))
    sleep 2
  done
  echo "$status"
}

regenerate_client_token=true
if [ -f "$CLIENT_TOKEN_FILE" ]; then
  EXISTING_TOKEN=$(cat "$CLIENT_TOKEN_FILE")
  http_status=$(client_token_status "$EXISTING_TOKEN")
  case "$http_status" in
    200)
      echo "X-Road client token is valid"
      regenerate_client_token=false
      ;;
    400|403)
      echo "Existing X-Road client token is invalid (HTTP $http_status)"
      ;;
    *)
      echo "Cannot verify the X-Road client token (HTTP $http_status); leaving it untouched" >&2
      exit 1
      ;;
  esac
fi

if [ "$regenerate_client_token" = "true" ] && [ -z "${BAO_TOKEN:-}" ]; then
  echo "The X-Road client token is missing or invalid and this node has no root token." >&2
  echo "Copy the root token file $ROOT_TOKEN_FILE from another node of the cluster (owner root:root, mode 600)," >&2
  echo "restart xroad-secret-store-local.service, then restart the X-Road services on this node." >&2
  exit 1
fi

if [ "$regenerate_client_token" = "true" ]; then
  rm -f "$CLIENT_TOKEN_FILE"
  echo "Generating X-Road client token.."
  # Use custom token ID if provided via environment variable (useful for dev/test)
  XROAD_SECRET_STORE_TOKEN_OVERRIDE="${XROAD_SECRET_STORE_TOKEN_OVERRIDE:-}"
  CLIENT_TOKEN=$(create_token "$BAO_ADDR" "$BAO_TOKEN" "xroad-policy" "0" "xroad-client" "$XROAD_SECRET_STORE_TOKEN_OVERRIDE") || {
    echo "Failed to create X-Road client token" >&2
    exit 1
  }
  echo "$CLIENT_TOKEN" > "$CLIENT_TOKEN_FILE"
  chmod 640 "$CLIENT_TOKEN_FILE"
  chown xroad:xroad "$CLIENT_TOKEN_FILE"
fi

if [ -n "${BAO_TOKEN:-}" ]; then
  echo "OpenBao setup completed"
else
  echo "OpenBao setup completed without root steps: mounts, policy and client token left as they are"
fi
