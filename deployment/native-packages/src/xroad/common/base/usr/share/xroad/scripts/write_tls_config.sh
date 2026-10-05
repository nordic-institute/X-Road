#!/bin/bash
#
# Script for storing the TLS certificate provisioning identity of X-Road modules:
# as configuration database rows (setup_default) or in a YAML file (write_tls_settings).
#
# This script provides functions to write TLS settings for X-Road modules.
# It handles splitting IP and DNS Subject Alternative Names (SANs) and writes
# the appropriate configuration values.
#
# Usage (when called directly):
#   write_tls_config.sh setup_default <module_name>  # Auto-detect hostname and IPs, store identity rows if absent
#   write_tls_config.sh <config_file> <module_name> <common_name> <alt_names>  # Explicit settings
#
# Usage (when sourced):
#   . write_tls_config.sh
#   setup_default_tls_config "proxy"           # Auto-detect, store identity rows if absent
#   write_tls_settings "$CONFIG_FILE" "proxy" "$cn" "$altn"  # Explicit settings
#

log () { echo >&2 "$@"; }

usage() {
  cat >&2 <<EOF
Usage:
  $0 setup_default <module_name>
  $0 <config_file> <module_name> <common_name> <alt_names>

Commands:
  setup_default - Auto-detect hostname and IPs, store identity rows in the database if absent

Arguments:
  module_name   - X-Road module name (e.g., proxy, op-monitor, proxy-ui-api)
  config_file   - Path to the YAML configuration file (e.g., /etc/xroad/conf.d/local-tls.yaml)
  common_name   - Common Name (CN) for the TLS certificate
  alt_names     - Alternative names in format: IP:1.1.1.1,DNS:name,IP:2.2.2.2,...

Examples:
  $0 setup_default proxy
  $0 /etc/xroad/conf.d/local-tls.yaml proxy host.example.com "IP:10.0.0.1,DNS:host.example.com"

EOF
  exit 1
}

# Extract the values of one family (IP or DNS) from a combined list, comma-joined.
# Arguments: $1 - family prefix, $2 - combined list
extract_alt_names() {
  printf '%s\n' "$2" | tr ',' '\n' \
    | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' \
    | sed -n -e "s/^$1:[[:space:]]*//p" \
    | sed -e '/^$/d' \
    | paste -sd, -
}

# Extract the IP alternative names (IPv4 and IPv6) from a combined IP/DNS list.
# Input format:
#   IP:1.1.1.1,DNS:example.com,IP:2.2.2.2,DNS:example.org
#   IP:10.0.2.15,IP:fd17:625c:f037:2:a00:27ff:fe7f:dfb6,DNS:example.org,DNS:example.org
# Whitespace around entries is ignored.
extract_ip_list() {
  extract_alt_names "IP" "$1"
}

# Extract the DNS alternative names from a combined IP/DNS list.
# Input format:
#   IP:1.1.1.1,DNS:example.com,IP:2.2.2.2,DNS:example.org
#   IP:10.0.2.15,IP:fd17:625c:f037:2:a00:27ff:fe7f:dfb6,DNS:example.org,DNS:example.org
# Whitespace around entries is ignored.
extract_dns_list() {
  extract_alt_names "DNS" "$1"
}

# Extract the bare common name from a subject answer.
# Accepts a bare host name, a /CN=host form or a full distinguished name in slash
# (/C=EE/O=Org/CN=host) or comma (CN=host, O=Org, C=EE) form; the CN component may appear anywhere.
extract_cn() {
  local subject="${1#"${1%%[![:space:]]*}"}"
  subject="${subject%"${subject##*[![:space:]]}"}"
  if [[ "$subject" == *[/,=]* ]]; then
    subject=$(echo "$subject" | tr '/,' '\n\n' | sed -n 's/^[[:space:]]*[Cc][Nn][[:space:]]*=[[:space:]]*//p' | head -n1)
    subject="${subject%"${subject##*[![:space:]]}"}"
  fi
  echo "$subject"
}

# Write the TLS identity rows of a module to the configuration database.
# Arguments:
#   $1 - module name (e.g., admin-service, management-service)
#   $2 - subject answer (bare host, /CN=host or full distinguished name)
#   $3 - Alternative names in format: IP:1.1.1.1,DNS:name,IP:2.2.2.2,...
#   $4 - optional, "reconfigure" to overwrite existing rows
# An answer without a common name falls back to the host name (hostname -f).
# Skips when XROAD_IGNORE_DATABASE_SETUP is set. Existing rows are kept unless reconfiguring.
# Returns non-zero, naming the key, on any database error.
write_tls_identity_rows() {
  local module_name="$1"
  local subject="$2"
  local altn="$3"
  local mode="${4:-}"
  local db_property="${DB_PROPERTY_SCRIPT:-/usr/share/xroad/scripts/db_property.sh}"

  if [[ -v XROAD_IGNORE_DATABASE_SETUP ]]; then
    log "XROAD_IGNORE_DATABASE_SETUP is set, not storing ${module_name} TLS identity rows"
    return 0
  fi

  local write_flag="--if-absent"
  if [[ "$mode" == "reconfigure" || "${DEBCONF_RECONFIGURE:-}" == "1" ]]; then
    write_flag="--yes"
  fi

  local prefix="xroad.${module_name}.tls.certificate-provisioning"
  local cn dns_list ip_list
  cn=$(extract_cn "$subject")
  if [[ -z "$cn" ]]; then
    cn=$(hostname -f 2>/dev/null || hostname 2>/dev/null || true)
    if [[ -z "$cn" ]]; then
      log "FATAL: no common name in the ${module_name} subject answer and no host name available"
      return 1
    fi
    log "No common name in the ${module_name} subject answer, using ${cn}"
  fi
  dns_list=$(extract_dns_list "$altn")
  ip_list=$(extract_ip_list "$altn")

  local key value
  for key in common-name alt-names ip-subject-alt-names; do
    case "$key" in
      common-name) value="$cn" ;;
      alt-names) value="$dns_list" ;;
      ip-subject-alt-names) value="$ip_list" ;;
    esac
    "$db_property" set "${prefix}.${key}" "$value" "$write_flag" \
      || { log "FATAL: failed to store ${prefix}.${key} in the database"; return 1; }
  done
}

# Write TLS certificate provisioning settings to configuration file
# Arguments:
#   $1 - config file path (e.g., /etc/xroad/conf.d/local-tls.yaml)
#   $2 - module name (e.g., proxy, op-monitor, proxy-ui-api)
#   $3 - Common Name (CN) for the certificate
#   $4 - Alternative names in format: IP:1.1.1.1,DNS:name,IP:2.2.2.2,...
write_tls_settings() {
  local config_file="$1"
  local module_name="$2"
  local cn="$3"
  local altn="$4"

  local ip_list dns_list
  ip_list=$(extract_ip_list "$altn")
  dns_list=$(extract_dns_list "$altn")

  log "Writing ${module_name} TLS settings to ${config_file}"
  /usr/share/xroad/scripts/yaml_helper.sh set "$config_file" "xroad.${module_name}.tls.certificate-provisioning.common-name" "$cn"
  /usr/share/xroad/scripts/yaml_helper.sh set "$config_file" "xroad.${module_name}.tls.certificate-provisioning.alt-names" "$dns_list"
  /usr/share/xroad/scripts/yaml_helper.sh set "$config_file" "xroad.${module_name}.tls.certificate-provisioning.ip-subject-alt-names" "$ip_list"
}

# Store the TLS identity rows of a module using the auto-detected host name and addresses.
# Existing rows are kept. Skips when XROAD_IGNORE_DATABASE_SETUP is set.
# Arguments:
#   $1 - module name (e.g., proxy, proxy-ui-api)
setup_default_tls_config() {
  local module_name="$1"

  local host alt_names
  host=$(hostname -f)
  if (( ${#host} > 64 )); then
    host=$(hostname -s)
  fi
  alt_names="$(ip addr | awk '/scope global/ {split($2,a,"/"); printf "IP:%s,", a[1]}')DNS:$(hostname -f),DNS:$(hostname -s)"

  write_tls_identity_rows "$module_name" "$host" "$alt_names"
}

# Main execution block - only runs when script is executed directly (not sourced)
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  if [[ "$1" == "setup_default" ]]; then
    if [[ -z "$2" ]]; then
      log "Error: module name required"
      usage
    fi
    setup_default_tls_config "$2"
  elif [[ $# -eq 4 ]]; then
    write_tls_settings "$1" "$2" "$3" "$4"
  else
    log "Error: Wrong number of arguments"
    usage
  fi
fi
