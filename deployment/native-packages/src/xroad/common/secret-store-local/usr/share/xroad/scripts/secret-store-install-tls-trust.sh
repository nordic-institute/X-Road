#!/bin/bash
#
# Installs the OpenBao TLS certificate into the system trust store and, on Debian-based
# systems, into the Java trust store.
set -euo pipefail

TLS_CERT_FILE=/opt/openbao/tls/tls.crt

if command -v update-ca-certificates >/dev/null 2>&1; then
  install -m 644 "$TLS_CERT_FILE" /usr/local/share/ca-certificates/openbao.crt
  update-ca-certificates
  if [ -x /var/lib/dpkg/info/ca-certificates-java.postinst ]; then
    /var/lib/dpkg/info/ca-certificates-java.postinst configure
  fi
elif command -v update-ca-trust >/dev/null 2>&1; then
  install -m 644 "$TLS_CERT_FILE" /etc/pki/ca-trust/source/anchors/openbao.crt
  update-ca-trust
else
  echo >&2 "Neither update-ca-certificates nor update-ca-trust is available"
  exit 1
fi
