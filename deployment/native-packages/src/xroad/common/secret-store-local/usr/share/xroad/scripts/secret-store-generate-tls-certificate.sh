#!/bin/bash
#
# Generates the OpenBao TLS certificate for the given subject alternative names.
# Without an argument the certificate names 127.0.0.1 only.
# Records the sha256 of the generated certificate in a root-owned directory that the openbao user cannot write.
#
# Usage: secret-store-generate-tls-certificate.sh [<subjectAltName list, e.g. IP:127.0.0.1,DNS:node.example>]

set -e

if [ $# -gt 1 ]; then
    echo >&2 "Usage: $0 [<subjectAltName list>]"
    exit 1
fi
SAN="${1:-IP:127.0.0.1}"
STATE_DIR=/var/lib/xroad-secret-store
GENERATED_HASH_FILE="${STATE_DIR}/generated-certificate.sha256"

echo "Generating OpenBao TLS certificates for X-Road..."
# Generate in temporary location first
TEMP_DIR=$(mktemp -d)
cd "$TEMP_DIR"

# Generate certificates with proper permissions
if ! openssl req \
    -out tls.crt \
    -new \
    -keyout tls.key \
    -newkey rsa:4096 \
    -nodes \
    -sha256 \
    -x509 \
    -subj "/O=OpenBao/CN=OpenBao" \
    -days 7300 \
    -addext "subjectAltName = ${SAN}" \
    -addext "keyUsage = digitalSignature,keyEncipherment" \
    -addext "extendedKeyUsage = serverAuth"; then
    echo "Failed to generate certificates"
    exit 1
fi

# Set proper permissions and ownership
chmod 640 tls.key tls.crt
chown openbao:openbao tls.key tls.crt
CERT_SHA256=$(sha256sum tls.crt | cut -d' ' -f1)

# Move files to final location; the record is absent while the certificate and the record may differ
install -d -m 0755 -o root -g root "$STATE_DIR"
rm -f "$GENERATED_HASH_FILE"
mkdir -p /opt/openbao/tls
mv -f tls.key tls.crt /opt/openbao/tls/
printf '%s\n' "$CERT_SHA256" >"$GENERATED_HASH_FILE"
chmod 644 "$GENERATED_HASH_FILE"

# Cleanup temp directory
rm -rf "$TEMP_DIR"
