#!/bin/bash
set -euo pipefail
NODE_CONFIG_DIR=/etc/xroad-secret-store
NODE_CONFIG_FILE="${NODE_CONFIG_DIR}/node.hcl"
ENV_FILE=/etc/openbao/xroad-openbao.env
SCRIPTS_DIR=/usr/share/xroad/scripts
TLS_SCRIPT="${SCRIPTS_DIR}/secret-store-generate-tls-certificate.sh"
TRUST_SCRIPT="${SCRIPTS_DIR}/secret-store-install-tls-trust.sh"
TLS_CERT_FILE=/opt/openbao/tls/tls.crt
TLS_KEY_FILE=/opt/openbao/tls/tls.key
STATE_DIR=/var/lib/xroad-secret-store
GENERATED_HASH_FILE="${STATE_DIR}/generated-certificate.sha256"
TRUST_MARKER_FILE="${STATE_DIR}/trusted-certificate.sha256"
LOCAL_ADDRESS=127.0.0.1
API_PORT=8200
CLUSTER_PORT=8201
NODE_ADDRESS=""
NODE_HOST=""
DISABLE_STANDBY_READS=false
SINGLE_NODE=false
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
  install -d -m 0755 -o root -g root "$(dirname "$file")" || die "Cannot write ${file}"
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
node_config() {
  local announce="${NODE_HOST:-$NODE_ADDRESS}"
  listener_block "$NODE_ADDRESS"
  if [ "$DISABLE_STANDBY_READS" = true ]; then
    echo 'disable_standby_reads = true'
  fi
  echo "api_addr = \"https://${announce}:${API_PORT}\""
  echo "cluster_addr = \"https://${announce}:${CLUSTER_PORT}\""
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
file_sha256() {
  sha256sum "$1" | cut -d' ' -f1
}
certificate_trusted() {
  [ -f "$TRUST_MARKER_FILE" ] && [ "$(cat "$TRUST_MARKER_FILE")" = "$1" ]
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
  die "Usage: $0 [--node-address <ipv4> [--node-host <dns>] [--disable-standby-reads] | --single-node]"
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
    --single-node)
      SINGLE_NODE=true
      shift
      ;;
    *)
      usage
      ;;
  esac
done
if [ "$SINGLE_NODE" = true ] && { [ -n "$NODE_ADDRESS" ] || [ -n "$NODE_HOST" ]; }; then
  usage
fi
if [ -n "$NODE_ADDRESS" ]; then
  valid_ipv4 "$NODE_ADDRESS" || die "--node-address must be an IPv4 address: ${NODE_ADDRESS}"
  host_has_address "$NODE_ADDRESS" || die "--node-address is not an IPv4 address of this host: ${NODE_ADDRESS}"
elif [ -n "$NODE_HOST" ]; then
  die "--node-host requires --node-address"
elif [ "$DISABLE_STANDBY_READS" = true ]; then
  die "--disable-standby-reads requires --node-address"
fi
if [ -n "$NODE_HOST" ]; then
  valid_host "$NODE_HOST" || die "--node-host must be a DNS name: ${NODE_HOST}"
fi
[ -s "$ENV_FILE" ] || die "The OpenBao database connection file ${ENV_FILE} is missing or empty. Create it by installing the Central Server or Security Server package, or import the cluster bundle."
if [ -z "$NODE_ADDRESS" ] && [ "$SINGLE_NODE" = false ] && [ -e "$NODE_CONFIG_FILE" ]; then
  die "${NODE_CONFIG_FILE} makes this host a cluster node. Run this script with the same --node-address and --node-host options as before, or with --single-node to make this host a single node."
fi
needs_restart=false
if [ -n "$NODE_ADDRESS" ]; then
  if write_if_changed "$NODE_CONFIG_FILE" 0644 root:root "$(node_config)"; then
    needs_restart=true
  fi
elif [ -e "$NODE_CONFIG_FILE" ]; then
  rm -f "$NODE_CONFIG_FILE" || die "Cannot remove ${NODE_CONFIG_FILE}"
  needs_restart=true
fi
if [ "$(wanted_cert_names)" != "$(current_cert_names)" ] || [ ! -f "$GENERATED_HASH_FILE" ]; then
  "$TLS_SCRIPT" "$(wanted_cert_names | paste -sd, -)"
fi
# The openbao user owns the certificate directory, so only a copy that matches the generated hash is trusted.
certificate_copy=$(mktemp)
trap 'rm -f "$certificate_copy"' EXIT
cp "$TLS_CERT_FILE" "$certificate_copy"
generated_hash="$(cat "$GENERATED_HASH_FILE")"
[ "$(file_sha256 "$certificate_copy")" = "$generated_hash" ] ||
  die "${TLS_CERT_FILE} is not the certificate generated by ${TLS_SCRIPT}; delete it and run this script again"
if ! certificate_trusted "$generated_hash"; then
  echo "Updating OpenBao certificate trust..."
  "$TRUST_SCRIPT" "$certificate_copy"
  write_if_changed "$TRUST_MARKER_FILE" 0644 root:root "$generated_hash" || true
  needs_restart=true
fi
if [ "$needs_restart" = true ] && [ -d /run/systemd/system ] && systemctl is-active --quiet openbao.service; then
  systemctl restart openbao.service
fi
