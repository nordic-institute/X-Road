#!/bin/bash
set -euo pipefail
CONFIG_FILE=/etc/openbao/xroad-openbao.hcl
DROP_IN_DIR=/etc/systemd/system/openbao.service.d
DROP_IN_FILE="${DROP_IN_DIR}/xroad.conf"
SCRIPTS_DIR=/usr/share/xroad/scripts
BAO_BINARY=/usr/bin/bao
UNSEAL_SCRIPT="${SCRIPTS_DIR}/secret-store-unseal.sh"
TLS_SCRIPT="${SCRIPTS_DIR}/secret-store-generate-tls-certificate.sh"
TRUST_SCRIPT="${SCRIPTS_DIR}/secret-store-install-tls-trust.sh"
TLS_CERT_FILE=/opt/openbao/tls/tls.crt
TLS_KEY_FILE=/opt/openbao/tls/tls.key
LOCAL_ADDRESS=127.0.0.1
API_PORT=8200
CLUSTER_PORT=8201
NODE_ADDRESS=""
NODE_HOST=""
DISABLE_STANDBY_READS=false
die() {
  echo >&2 "$@"
  exit 1
}

# write_if_changed FILE MODE OWNER:GROUP CONTENT; returns 0 when the file was written, 1 when unchanged.
# It runs as an if condition, where errexit is off, so a failed write ends the script here.
write_if_changed() {
  local file="$1" mode="$2" owner="$3" content="$4" tmp
  if [ -f "$file" ] && [ "$(cat "$file")" = "$content" ]; then
    return 1
  fi
  mkdir -p "$(dirname "$file")" || die "Cannot write ${file}"
  tmp=$(mktemp "${file}.XXXXXX") || die "Cannot write ${file}"
  if ! { printf '%s\n' "$content" >"$tmp" && chown "$owner" "$tmp" && chmod "$mode" "$tmp" && mv -f "$tmp" "$file"; }; then
    rm -f "$tmp"
    die "Cannot write ${file}"
  fi
}

listener_block() {
  cat <<EOF
listener "tcp" {
  address       = "$1:${API_PORT}"
  tls_cert_file = "${TLS_CERT_FILE}"
  tls_key_file  = "${TLS_KEY_FILE}"
}
EOF
}
server_config() {
  local announce="${NODE_HOST:-$NODE_ADDRESS}"
  echo 'ui = true'
  listener_block "$LOCAL_ADDRESS"
  if [ -n "$NODE_ADDRESS" ]; then
    listener_block "$NODE_ADDRESS"
  fi
  cat <<EOF
storage "postgresql" {
  ha_enabled = "true"
}
EOF
  if [ "$DISABLE_STANDBY_READS" = true ]; then
    echo 'disable_standby_reads = true'
  fi
  if [ -n "$NODE_ADDRESS" ]; then
    echo "api_addr = \"https://${announce}:${API_PORT}\""
    echo "cluster_addr = \"https://${announce}:${CLUSTER_PORT}\""
  else
    echo "api_addr = \"https://${LOCAL_ADDRESS}:${API_PORT}\""
  fi
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
wanted_cert_names() {
  {
    echo "IP:${LOCAL_ADDRESS}"
    [ -z "$NODE_ADDRESS" ] || echo "IP:${NODE_ADDRESS}"
    [ -z "$NODE_HOST" ] || echo "DNS:${NODE_HOST}"
  } | sort -u
}
current_cert_names() {
  [ -f "$TLS_CERT_FILE" ] || return 0
  openssl x509 -in "$TLS_CERT_FILE" -noout -ext subjectAltName 2>/dev/null |
    tr ',' '\n' | sed -n -e 's/^[[:space:]]*IP Address:/IP:/p' -e 's/^[[:space:]]*DNS:/DNS:/p' | sort -u
}
valid_ipv4() {
  local octet
  [[ "$1" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]] || return 1
  for octet in ${1//./ }; do
    [ "$((10#$octet))" -le 255 ] || return 1
  done
}
host_has_address() {
  ip -4 -o addr show | awk -v address="$1" '{ split($4, parts, "/"); if (parts[1] == address) found = 1 } END { exit !found }'
}
valid_host() {
  [ "${#1}" -le 253 ] && [[ "$1" =~ ^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$ ]]
}
usage() {
  die "Usage: $0 [--node-address <ipv4>] [--node-host <dns>] [--disable-standby-reads]"
}
while [ $# -gt 0 ]; do
  case "$1" in
    --node-address)
      [ $# -ge 2 ] || usage
      NODE_ADDRESS="$2"
      shift 2
      ;;
    --node-host)
      [ $# -ge 2 ] || usage
      NODE_HOST="$(echo "$2" | tr '[:upper:]' '[:lower:]')"
      shift 2
      ;;
    --disable-standby-reads)
      DISABLE_STANDBY_READS=true
      shift
      ;;
    *)
      usage
      ;;
  esac
done
if [ -n "$NODE_ADDRESS" ]; then
  valid_ipv4 "$NODE_ADDRESS" || die "--node-address must be an IPv4 address: ${NODE_ADDRESS}"
  host_has_address "$NODE_ADDRESS" || die "--node-address is not an IPv4 address of this host: ${NODE_ADDRESS}"
elif [ -n "$NODE_HOST" ]; then
  die "--node-host requires --node-address"
fi
if [ -n "$NODE_HOST" ]; then
  valid_host "$NODE_HOST" || die "--node-host must be a DNS name: ${NODE_HOST}"
fi
needs_restart=false
if write_if_changed "$CONFIG_FILE" 0640 openbao:xroad "$(server_config)"; then
  needs_restart=true
fi
if write_if_changed "$DROP_IN_FILE" 0644 root:root "$(drop_in)"; then
  if [ -d /run/systemd/system ]; then
    systemctl daemon-reload
  fi
fi
if [ "$(wanted_cert_names)" != "$(current_cert_names)" ]; then
  "$TLS_SCRIPT" "$(wanted_cert_names | paste -sd, -)"
  "$TRUST_SCRIPT"
  needs_restart=true
fi
if [ "$needs_restart" = true ] && [ -d /run/systemd/system ] && systemctl is-active --quiet openbao.service; then
  systemctl restart openbao.service
fi
