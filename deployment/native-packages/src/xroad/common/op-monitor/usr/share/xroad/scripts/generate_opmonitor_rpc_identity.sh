#!/bin/bash
set -euo pipefail

DEFAULT_DIR=/etc/xroad/ssl/opmonitor-rpc
DIR=${XROAD_OPMONITOR_IDENTITY_DIR:-$DEFAULT_DIR}
SERVER_USER=xroad
DEFAULT_DAYS=3650
DEFAULT_CA_DAYS=7300
EXPIRY_MARGIN_SECONDS=300
CA_SUBJECT="/CN=X-Road op-monitor CA"
SERVER_SUBJECT="/CN=X-Road op-monitor"

usage() {
  cat <<EOF
usage: $0 [--days <n>] [--ca-days <n>] [--force-new-ca] <dns-name|ip-address>...

Creates the op-monitor RPC identity in $DEFAULT_DIR:
  ca.crt      CA certificate (public)
  ca.key      CA private key (readable by root only)
  server.key  server private key, unencrypted PKCS#8 (readable by $SERVER_USER only)
  server.crt  server certificate signed by the CA (public)

A rerun keeps the CA and replaces the server key and certificate.

OPTIONS:
  --days <n>       server certificate validity in days, default $DEFAULT_DAYS
  --ca-days <n>    CA validity in days, default $DEFAULT_CA_DAYS; used when a CA is created or replaced
  --force-new-ca   replace the existing CA
  -h, --help       show this message

At least one DNS name or IP address is required; they become the subject alternative names of the server certificate.
Keys are RSA 2048.
EOF
}

die() {
  echo "error: $*" >&2
  exit 1
}

quiet_openssl() {
  local output
  if ! output=$(openssl "$@" 2>&1); then
    echo "$output" >&2
    die "openssl $1 failed"
  fi
}

require_days() {
  local option=$1 value=$2
  [[ $value =~ ^[1-9][0-9]{0,5}$ ]] || die "$option must be a positive whole number of days, at most 6 digits"
}

days=$DEFAULT_DAYS
ca_days=$DEFAULT_CA_DAYS
ca_days_given=
force_new_ca=
names=()

while (($# > 0)); do
  case $1 in
    -h | --help)
      usage
      exit 0
      ;;
    --days)
      (($# >= 2)) || die "--days requires a value"
      days=$2
      shift 2
      ;;
    --ca-days)
      (($# >= 2)) || die "--ca-days requires a value"
      ca_days=$2
      ca_days_given=1
      shift 2
      ;;
    --force-new-ca)
      force_new_ca=1
      shift
      ;;
    -*)
      usage >&2
      die "unknown option $1"
      ;;
    *)
      names+=("$1")
      shift
      ;;
  esac
done

if ((${#names[@]} == 0)); then
  usage >&2
  die "at least one DNS name or IP address is required"
fi

require_days --days "$days"
require_days --ca-days "$ca_days"

LC_ALL=C

octet='(0|[1-9][0-9]{0,2})'
ipv4_pattern="^${octet}(\.${octet}){3}\$"
numeric_pattern='^[0-9.]+$'
dns_label='[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?'
dns_pattern="^${dns_label}(\.${dns_label})*\$"
hex_group='[0-9A-Fa-f]{1,4}'
ipv6_chars='^[0-9A-Fa-f:.]+$'
ipv6_groups="^(${hex_group}:)*${hex_group}\$"
ipv6_full="^(${hex_group}:){7}${hex_group}\$"

valid_ipv4() {
  local value=$1 part
  local -a parts
  [[ $value =~ $ipv4_pattern ]] || return 1
  IFS=. read -ra parts <<<"$value"
  for part in "${parts[@]}"; do
    ((part <= 255)) || return 1
  done
}

valid_dns() {
  local value=$1
  ((${#value} <= 253)) && [[ $value =~ $dns_pattern ]]
}

count_groups() {
  local colons
  if [[ -z $1 ]]; then
    group_count=0
  else
    colons=${1//[^:]/}
    group_count=$((${#colons} + 1))
  fi
}

valid_ipv6() {
  local value=$1 embedded left right
  [[ $value =~ $ipv6_chars ]] || return 1
  if [[ $value == *.* ]]; then
    embedded=${value##*:}
    valid_ipv4 "$embedded" || return 1
    value=${value%"$embedded"}0:0
  fi
  if [[ $value == *::* ]]; then
    left=${value%%::*}
    right=${value#*::}
    [[ $right != *::* && $right != :* ]] || return 1
    [[ -z $left || $left =~ $ipv6_groups ]] || return 1
    [[ -z $right || $right =~ $ipv6_groups ]] || return 1
    count_groups "$left"
    local left_count=$group_count
    count_groups "$right"
    ((left_count + group_count <= 7))
  else
    [[ $value =~ $ipv6_full ]]
  fi
}

san=
for name in "${names[@]}"; do
  if [[ $name == *:* ]]; then
    valid_ipv6 "$name" || die "'$name' is not a valid IPv6 address"
    entry="IP:$name"
  elif [[ $name =~ $numeric_pattern ]]; then
    valid_ipv4 "$name" || die "'$name' is not a valid IPv4 address"
    entry="IP:$name"
  else
    valid_dns "$name" || die "'$name' is not a valid DNS name"
    entry="DNS:$name"
  fi
  san="${san:+$san,}$entry"
done

command -v openssl >/dev/null 2>&1 || die "openssl is not installed"

change_owner=
if [[ $(id -u) -eq 0 ]]; then
  id "$SERVER_USER" >/dev/null 2>&1 || die "user $SERVER_USER does not exist"
  change_owner=1
elif [[ -z ${XROAD_OPMONITOR_IDENTITY_DIR:-} ]]; then
  die "must be run as root"
fi

ca_crt=$DIR/ca.crt
ca_key=$DIR/ca.key
new_ca=
if [[ -n $force_new_ca ]]; then
  new_ca=1
elif [[ -e $ca_crt && -e $ca_key ]]; then
  [[ -z $ca_days_given ]] || echo "note: --ca-days ignored, the existing CA is kept" >&2
elif [[ ! -e $ca_crt && ! -e $ca_key ]]; then
  new_ca=1
else
  die "$DIR holds only one of ca.crt and ca.key; restore the missing file or use --force-new-ca"
fi

if [[ -n $new_ca ]]; then
  ((days < ca_days)) || die "server certificate validity ($days days) must be shorter than CA validity ($ca_days days)"
else
  openssl x509 -in "$ca_crt" -noout -checkend $((days * 86400 + EXPIRY_MARGIN_SECONDS)) >/dev/null ||
    die "the existing CA expires before a server certificate valid for $days days; use a smaller --days or --force-new-ca"
fi

dir_created=
if [[ ! -d $DIR ]]; then
  (umask 022 && mkdir -p "$DIR")
  dir_created=1
fi
umask 077
stage=$(mktemp -d "$DIR/.stage.XXXXXX")
cleanup() {
  [[ -z $stage ]] || rm -rf "$stage"
  [[ -z $dir_created ]] || rmdir "$DIR" 2>/dev/null || true
}
trap cleanup EXIT

cat >"$stage/openssl.cnf" <<EOF
[req]
distinguished_name = dn
prompt = no

[dn]
CN = X-Road op-monitor

[ca_ext]
basicConstraints = critical,CA:TRUE
keyUsage = critical,keyCertSign,cRLSign
subjectKeyIdentifier = hash

[server_ext]
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth,clientAuth
subjectAltName = $san
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always
EOF

if [[ -n $new_ca ]]; then
  signing_crt=$stage/ca.crt
  signing_key=$stage/ca.key
  quiet_openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$signing_key"
  quiet_openssl req -new -x509 -sha256 -key "$signing_key" -days "$ca_days" \
    -config "$stage/openssl.cnf" -extensions ca_ext -subj "$CA_SUBJECT" -out "$signing_crt"
else
  signing_crt=$ca_crt
  signing_key=$ca_key
fi

quiet_openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$stage/server.key"
grep -q -- '-----BEGIN PRIVATE KEY-----' "$stage/server.key" || die "the generated server key is not PKCS#8"
quiet_openssl req -new -key "$stage/server.key" -config "$stage/openssl.cnf" -subj "$SERVER_SUBJECT" \
  -out "$stage/server.csr"
quiet_openssl x509 -req -in "$stage/server.csr" -CA "$signing_crt" -CAkey "$signing_key" \
  -set_serial "0x$(openssl rand -hex 16)" -days "$days" -sha256 \
  -extfile "$stage/openssl.cnf" -extensions server_ext -out "$stage/server.crt"
quiet_openssl verify -CAfile "$signing_crt" "$stage/server.crt"

chmod 0644 "$stage/server.crt"
chmod 0400 "$stage/server.key"
if [[ -n $new_ca ]]; then
  chmod 0644 "$stage/ca.crt"
  chmod 0400 "$stage/ca.key"
fi
if [[ -n $change_owner ]]; then
  chown "$SERVER_USER:$SERVER_USER" "$stage/server.key"
  if [[ -n $new_ca ]]; then
    chown root:root "$stage/ca.crt" "$stage/ca.key"
  fi
fi

if [[ -n $new_ca ]]; then
  mv -f "$stage/ca.crt" "$ca_crt"
  mv -f "$stage/ca.key" "$ca_key"
fi
mv -f "$stage/server.key" "$DIR/server.key"
mv -f "$stage/server.crt" "$DIR/server.crt"

if [[ -n $new_ca ]]; then
  echo "Created a new CA: $ca_crt, $ca_key"
  echo "Import $ca_crt into the Security Server."
else
  echo "Kept the existing CA: $ca_crt"
fi
echo "Created the server identity: $DIR/server.key, $DIR/server.crt"
echo "Restart op-monitor to load it."
