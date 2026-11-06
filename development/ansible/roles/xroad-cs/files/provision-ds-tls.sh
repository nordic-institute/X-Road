#!/bin/bash
# Gives the Issuer Service a TLS certificate through the Central Server admin API:
# generates the key, requests a CSR for the given DNS name, signs it with a throwaway CA
# and uploads the chain. Does nothing when a certificate is already present.
# Usage: provision-ds-tls.sh <dns-name>; the user comes from XROAD_ADMIN_USER, the password from the standard input.
set -euo pipefail

san="$1"
IFS= read -r admin_password || [[ -n "$admin_password" ]]
base="https://localhost:4000"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

printf '%s' "$admin_password" | curl -sk -f -c "$work/jar" -o /dev/null \
  --data-urlencode "username=${XROAD_ADMIN_USER}" --data-urlencode "password@-" \
  "$base/login"
token="$(awk '$6 == "XSRF-TOKEN" {print $7}' "$work/jar")"
api() { curl -sk -f -b "$work/jar" -H "X-XSRF-TOKEN: $token" "$@"; }

status="$(api "$base/api/v1/ds-tls-certificate")"
if grep -q '"certificate"' <<<"$status"; then
  echo "DS TLS certificate already present"
  exit 0
fi

if ! grep -q '"key_generated":true' <<<"$status"; then
  api -X POST -o /dev/null "$base/api/v1/ds-tls-certificate/key"
fi

api -X POST -H 'Content-Type: application/json' \
  -d "{\"name\":\"CN=${san}\"}" \
  -o "$work/request.csr" "$base/api/v1/ds-tls-certificate/csr"
csr_form=PEM
grep -q 'BEGIN' "$work/request.csr" || csr_form=DER

openssl req -x509 -newkey rsa:2048 -nodes -days 3650 -subj "/CN=Development DS TLS CA" \
  -keyout "$work/ca.key" -out "$work/ca.crt" 2>/dev/null
printf 'subjectAltName=DNS:%s\n' "$san" >"$work/ext.cnf"
openssl x509 -req -inform "$csr_form" -in "$work/request.csr" -days 825 \
  -CA "$work/ca.crt" -CAkey "$work/ca.key" -CAcreateserial -extfile "$work/ext.cnf" \
  -out "$work/leaf.crt" 2>/dev/null
cat "$work/leaf.crt" "$work/ca.crt" >"$work/chain.pem"

api -X POST -o /dev/null -F "certificate=@$work/chain.pem;type=application/octet-stream" \
  "$base/api/v1/ds-tls-certificate/certificate"
echo "DS TLS certificate provisioned for ${san}"
