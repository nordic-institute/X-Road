#!/bin/bash
#
# Generates the OpenBao TLS certificate. The certificate always names 127.0.0.1.
#
# Usage: secret-store-generate-tls-certificate.sh [--node-address <ip>] [--node-host <dns>]

set -e

SAN="IP:127.0.0.1"
while [ $# -gt 0 ]; do
    case "$1" in
        --node-address)
            [ $# -ge 2 ] || { echo >&2 "Missing value for $1"; exit 1; }
            SAN="${SAN},IP:$2"
            shift 2
            ;;
        --node-host)
            [ $# -ge 2 ] || { echo >&2 "Missing value for $1"; exit 1; }
            SAN="${SAN},DNS:$2"
            shift 2
            ;;
        *)
            echo >&2 "Usage: $0 [--node-address <ip>] [--node-host <dns>]"
            exit 1
            ;;
    esac
done

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
