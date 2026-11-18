#!/bin/bash
#
# Generates the OpenBao TLS certificate for the given subject alternative names.
# Without an argument the certificate names 127.0.0.1 only.
#
# Usage: secret-store-generate-tls-certificate.sh [<subjectAltName list, e.g. IP:127.0.0.1,DNS:node.example>]

set -e

if [ $# -gt 1 ]; then
    echo >&2 "Usage: $0 [<subjectAltName list>]"
    exit 1
fi
SAN="${1:-IP:127.0.0.1}"

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

# Move files to final location
mkdir -p /opt/openbao/tls
mv -f tls.key tls.crt /opt/openbao/tls/

# Cleanup temp directory
rm -rf "$TEMP_DIR"
