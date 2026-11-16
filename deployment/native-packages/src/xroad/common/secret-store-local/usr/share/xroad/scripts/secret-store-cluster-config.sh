#!/bin/bash
#
# Writes the X-Road OpenBao server configuration and the systemd drop-in that makes
# openbao.service read only that file, unseal OpenBao after every start and restart it on
# failure. The OpenBao package's own configuration file is
# not read. The database connection comes from /etc/openbao/openbao.env.
#
# Usage: secret-store-cluster-config.sh
set -euo pipefail

CONFIG_FILE=/etc/openbao/xroad-openbao.hcl
DROP_IN_DIR=/etc/systemd/system/openbao.service.d
DROP_IN_FILE="${DROP_IN_DIR}/xroad.conf"
BAO_BINARY=/usr/bin/bao
UNSEAL_SCRIPT=/usr/share/xroad/scripts/secret-store-unseal.sh
TLS_CERT_FILE=/opt/openbao/tls/tls.crt
TLS_KEY_FILE=/opt/openbao/tls/tls.key
LOCAL_ADDRESS=127.0.0.1
API_PORT=8200

die() {
  echo >&2 "$@"
  exit 1
}

# write_if_changed FILE MODE OWNER:GROUP CONTENT; returns 0 when the file was written, 1 when unchanged.
write_if_changed() {
  local file="$1" mode="$2" owner="$3" content="$4" tmp
  if [ -f "$file" ] && [ "$(cat "$file")" = "$content" ]; then
    return 1
  fi
  mkdir -p "$(dirname "$file")"
  tmp=$(mktemp "${file}.XXXXXX")
  printf '%s\n' "$content" >"$tmp"
  chown "$owner" "$tmp"
  chmod "$mode" "$tmp"
  mv -f "$tmp" "$file"
}

server_config() {
  cat <<EOF
ui = true

listener "tcp" {
  address       = "${LOCAL_ADDRESS}:${API_PORT}"
  tls_cert_file = "${TLS_CERT_FILE}"
  tls_key_file  = "${TLS_KEY_FILE}"
}

storage "postgresql" {
  ha_enabled = "true"
}

api_addr = "https://${LOCAL_ADDRESS}:${API_PORT}"
EOF
}

drop_in() {
  cat <<EOF
[Service]
ExecStart=
ExecStart=${BAO_BINARY} server -config=${CONFIG_FILE}
ExecStartPost=-+${UNSEAL_SCRIPT}
Restart=on-failure
RestartSec=5
EOF
}

[ $# -eq 0 ] || die "Usage: $0"

write_if_changed "$CONFIG_FILE" 0640 openbao:xroad "$(server_config)" || true

if write_if_changed "$DROP_IN_FILE" 0644 root:root "$(drop_in)"; then
  if [ -d /run/systemd/system ]; then
    systemctl daemon-reload
  fi
fi
