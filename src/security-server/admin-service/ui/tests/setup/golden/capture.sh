#!/usr/bin/env bash
# Capture golden fixtures from a live Security Server API stack.
#
# Usage:
#   SS_HOST=https://localhost:4200 SS_USER=xrd SS_PASS=secret123! bash capture.sh
#
# The stack can be started via the api-test intTest task or the docker-compose
# dev stack in core/development/docker/security-server/compose.yaml.
#
# On a warm api-test stack (after running ./gradlew intTest once), the owner
# client (DEV:COM:1234) and at least one subsystem seeded by the tests will be
# present. The script captures those specific responses.

set -euo pipefail

SS_HOST="${SS_HOST:-https://localhost:4200}"
SS_USER="${SS_USER:-xrd}"
SS_PASS="${SS_PASS:-secret123!}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Authenticating against $SS_HOST ..."
COOKIE_JAR="$(mktemp)"
trap 'rm -f "$COOKIE_JAR"' EXIT

curl -s -k -c "$COOKIE_JAR" -b "$COOKIE_JAR" \
  -X POST "$SS_HOST/login" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "username=$SS_USER" \
  --data-urlencode "password=$SS_PASS" > /dev/null

XSRF_TOKEN="$(grep XSRF-TOKEN "$COOKIE_JAR" | awk '{print $7}' | head -1)"
if [[ -z "$XSRF_TOKEN" ]]; then
  echo "ERROR: XSRF token not found — authentication may have failed." >&2
  exit 1
fi

CURL="curl -s -k -c $COOKIE_JAR -b $COOKIE_JAR -H X-XSRF-TOKEN:$XSRF_TOKEN"

echo "Capturing GET /api/v1/clients ..."
$CURL "$SS_HOST/api/v1/clients?internal_search=true" \
  | python3 -m json.tool > "$SCRIPT_DIR/clients.json"

echo "Capturing POST /api/v1/clients/:id/service-descriptions (REST) ..."
CLIENT_ID="DEV:COM:1234:SUBS1"
ENCODED_CLIENT_ID="$(python3 -c "import urllib.parse; print(urllib.parse.quote('$CLIENT_ID', safe=''))")"
SD_RESPONSE="$(
  $CURL -X POST "$SS_HOST/api/v1/clients/$ENCODED_CLIENT_ID/service-descriptions" \
    -H "Content-Type: application/json" \
    -d "{\"url\":\"https://example.com/rest-api\",\"rest_service_code\":\"MY-API\",\"type\":\"REST\",\"ignore_warnings\":false}" \
    2>/dev/null || true
)"
if echo "$SD_RESPONSE" | python3 -m json.tool > "$SCRIPT_DIR/service-description-created.json" 2>/dev/null; then
  echo "  Captured service-description-created.json"
else
  echo "  WARNING: POST service-descriptions failed — keeping existing golden file." >&2
fi

echo "Done. Review the captured files before committing them."
