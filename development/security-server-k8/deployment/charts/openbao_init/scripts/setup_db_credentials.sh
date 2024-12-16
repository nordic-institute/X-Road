#!/bin/sh
set -e
. /scripts/_common.sh

# TODO This script is purely for dev purposes. Eventually it should be removed once we design password policy.
echo "[SETUP] Starting Postgres credential setup..."

# Get root token
INIT_SECRET=$(k8s_api "GET" "/api/v1/namespaces/${NAMESPACE}/secrets/${SECRET_NAME}" \
  "" "Retrieving init secret")

ROOT_TOKEN=$(echo "$INIT_SECRET" | jq -r '.data."root_token"' | base64 -d)
if [ -z "$ROOT_TOKEN" ]; then
  echo "[SETUP] Failed to get root token"
  exit 1
fi

# Store serverconf secret
bao_api "PUT" "/v1/xrd-secret/serverconf" \
  "{\"username\":\"${SERVERCONF_USERNAME}\",\"password\":\"${SERVERCONF_PASSWORD}\"}" "$ROOT_TOKEN" "Creating serverconf secret" >/dev/null

# Store messagelog secret
bao_api "PUT" "/v1/xrd-secret/messagelog" \
  "{\"username\":\"${MESSAGELOG_USERNAME}\",\"password\":\"${MESSAGELOG_PASSWORD}\"}" "$ROOT_TOKEN" "Creating messagelog secret" >/dev/null

# Store DS secret
bao_api "PUT" "/v1/xrd-secret/ds-control-plane" \
  "{\"username\":\"${DS_USERNAME}\",\"password\":\"${DS_PASSWORD}\"}" "$ROOT_TOKEN" "Creating DS secret" >/dev/null
bao_api "PUT" "/v1/xrd-secret/ds-data-plane" \
  "{\"username\":\"${DS_USERNAME}\",\"password\":\"${DS_PASSWORD}\"}" "$ROOT_TOKEN" "Creating DS secret" >/dev/null
bao_api "PUT" "/v1/xrd-secret/ds-identity-hub" \
  "{\"username\":\"${DS_USERNAME}\",\"password\":\"${DS_PASSWORD}\"}" "$ROOT_TOKEN" "Creating DS secret" >/dev/null

echo "[SETUP] Credential creation complete"
