#!/bin/bash
# Provisions the DS TLS certificate into this container's local secret store (OpenBao) before
# xroad-ds-issuer-service starts, so that startup (see cs-xroad.conf) does not depend on any
# externally-mounted keystore file.
#
# Runs behind wait-for-secret-store.sh (cs-xroad.conf), so the local secret store token already
# exists by the time this script starts; it is not the general secret-store readiness gate. If a
# `testca` host is reachable on the network (the SS e2e-test compose stack has one; the Central
# Server admin-service api-test stack does not), the certificate is signed by it, so the same CA
# can be designated as a DS TLS CA in globalconf for real, fail-closed trust testing. Otherwise
# this falls back to a self-signed certificate, matching the retired ds-https-keystore.yml
# recipe's own behaviour in stacks without a test CA.

set -euo pipefail

SECRET_STORE_LOCAL_CONF="/etc/xroad/services/secret-store-local.conf"
DS_TLS_TESTCA_HOST="${DS_TLS_TESTCA_HOST:-testca}"
DS_TLS_TESTCA_PORT="${DS_TLS_TESTCA_PORT:-8888}"
DS_TLS_SAN="${DS_TLS_SAN:-DNS:localhost,IP:127.0.0.1}"
DS_TLS_CN="${XROAD_HOST:-localhost}"

log() { echo "$(date --utc -Iseconds) INFO [provision-ds-tls-cert] $*"; }
warn() { echo "$(date --utc -Iseconds) WARN [provision-ds-tls-cert] $*" >&2; }

if [[ ! -f "$SECRET_STORE_LOCAL_CONF" ]]; then
  warn "$SECRET_STORE_LOCAL_CONF not found - secret store not initialized yet, skipping."
  exit 0
fi

set +u
# shellcheck source=/dev/null
source "$SECRET_STORE_LOCAL_CONF"
set -u
SECRET_STORE_ADDR="${XROAD_SECRET_STORE_SCHEME}://${XROAD_SECRET_STORE_HOST}:${XROAD_SECRET_STORE_PORT}"

existing_status=$(curl -fsS -o /dev/null -w '%{http_code}' \
  -H "X-Vault-Token: ${XROAD_SECRET_STORE_TOKEN}" \
  "${SECRET_STORE_ADDR}/v1/xrd-secret/tls/ds-https" || true)
if [[ "$existing_status" == "200" ]]; then
  log "DS TLS certificate already present at xrd-secret/tls/ds-https, skipping."
  exit 0
fi

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT
cd "$workdir"

openssl genrsa -out ds-https-key.pem 2048 2>/dev/null

testca_reachable=false
for _ in $(seq 1 10); do
  if curl -fsS -o /dev/null "http://${DS_TLS_TESTCA_HOST}:${DS_TLS_TESTCA_PORT}/testca/certs/ca.cert.pem" 2>/dev/null; then
    testca_reachable=true
    break
  fi
  sleep 1
done

if [[ "$testca_reachable" == true ]]; then
  log "Signing DS TLS certificate with ${DS_TLS_TESTCA_HOST}:${DS_TLS_TESTCA_PORT}"
  openssl req -new -key ds-https-key.pem -out ds-https.csr -subj "/CN=${DS_TLS_CN}" \
    -addext "subjectAltName=${DS_TLS_SAN}"
  curl -fsS -X POST -F "type=auth" -F "certreq=@ds-https.csr" \
    "http://${DS_TLS_TESTCA_HOST}:${DS_TLS_TESTCA_PORT}/testca/sign" -o ds-https-cert.pem
  curl -fsS "http://${DS_TLS_TESTCA_HOST}:${DS_TLS_TESTCA_PORT}/testca/certs/ca.cert.pem" -o ds-https-ca.pem
  cat ds-https-cert.pem ds-https-ca.pem >ds-https-chain.pem
else
  log "No test CA reachable at ${DS_TLS_TESTCA_HOST}:${DS_TLS_TESTCA_PORT}, falling back to a self-signed certificate"
  openssl req -x509 -key ds-https-key.pem -out ds-https-chain.pem -days 3650 \
    -subj "/CN=${DS_TLS_CN}" -addext "subjectAltName=${DS_TLS_SAN}"
fi

cert_content=$(sed ':a;N;$!ba;s/\n/\\n/g' ds-https-chain.pem)
key_content=$(sed ':a;N;$!ba;s/\n/\\n/g' ds-https-key.pem)
payload="{\"certificate\": \"${cert_content}\", \"privateKey\": \"${key_content}\"}"

curl -fsS -X POST \
  -H "X-Vault-Token: ${XROAD_SECRET_STORE_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "$payload" \
  "${SECRET_STORE_ADDR}/v1/xrd-secret/tls/ds-https"

log "DS TLS certificate written to xrd-secret/tls/ds-https"
