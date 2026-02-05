#!/bin/bash
#
# Script for writing TLS certificate provisioning configuration to YAML files.
#
# This script provides functions to write TLS settings for X-Road modules.
# It handles splitting IP and DNS Subject Alternative Names (SANs) and writes
# the appropriate configuration values.
#
# Usage (when called directly):
#   write_tls_config.sh setup <module_name>     # Auto-detect hostname and IPs, skip if already configured
#   write_tls_config.sh <config_file> <module_name> <common_name> <alt_names>  # Explicit settings
#
# Usage (when sourced):
#   . write_tls_config.sh
#   setup_tls_config "proxy"                    # Auto-detect, skip if configured
#   write_tls_settings "$CONFIG_FILE" "proxy" "$cn" "$altn"  # Explicit settings
#

log () { echo >&2 "$@"; }

usage() {
  cat >&2 <<EOF
Usage:
  $0 setup <module_name>
  $0 <config_file> <module_name> <common_name> <alt_names>

Commands:
  setup         - Auto-detect hostname and IPs, skip if already configured

Arguments:
  module_name   - X-Road module name (e.g., proxy, op-monitor, proxy-ui-api)
  config_file   - Path to the YAML configuration file (e.g., /etc/xroad/conf.d/local-tls.yaml)
  common_name   - Common Name (CN) for the TLS certificate
  alt_names     - Alternative names in format: IP:1.1.1.1,DNS:name,IP:2.2.2.2,...

Examples:
  $0 setup proxy
  $0 /etc/xroad/conf.d/local-tls.yaml proxy host.example.com "IP:10.0.0.1,DNS:host.example.com"

EOF
  exit 1
}

# Split combined IP and DNS alternative names into separate lists
# Input format: IP:1.1.1.1,DNS:example.com,IP:2.2.2.2,DNS:example.org
# Output: Two space-separated values: "ip_list" "dns_list"
split_ip_dns() {
  local input="$1"
  local ip_list dns_list
  ip_list=$(echo "$input" | tr ',' '\n' | grep '^IP:' | cut -d: -f2 | paste -sd,)
  dns_list=$(echo "$input" | tr ',' '\n' | grep '^DNS:' | cut -d: -f2 | paste -sd,)
  echo "$ip_list" "$dns_list"
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

  read ip_list dns_list < <(split_ip_dns "$altn")

  log "Writing ${module_name} TLS settings to ${config_file}"
  /usr/share/xroad/scripts/yaml_helper.sh set "$config_file" "xroad.${module_name}.tls.certificate-provisioning.common-name" "$cn"
  /usr/share/xroad/scripts/yaml_helper.sh set "$config_file" "xroad.${module_name}.tls.certificate-provisioning.alt-names" "$dns_list"
  /usr/share/xroad/scripts/yaml_helper.sh set "$config_file" "xroad.${module_name}.tls.certificate-provisioning.ip-subject-alt-names" "$ip_list"
}

# Setup TLS config for a module with auto-detected hostname and IPs.
# Skips if already configured.
# Arguments:
#   $1 - module name (e.g., proxy, op-monitor, proxy-ui-api)
setup_tls_config() {
  local module_name="$1"
  local config_file="/etc/xroad/conf.d/local-tls.yaml"
  local yaml_key_prefix="xroad.${module_name}.tls.certificate-provisioning"

  if ! /usr/share/xroad/scripts/yaml_helper.sh exists "$config_file" "${yaml_key_prefix}.common-name" &>/dev/null \
     && ! /usr/share/xroad/scripts/yaml_helper.sh exists "$config_file" "${yaml_key_prefix}.alt-names" &>/dev/null \
     && ! /usr/share/xroad/scripts/yaml_helper.sh exists "$config_file" "${yaml_key_prefix}.ip-subject-alt-names" &>/dev/null; then

    local host alt_names
    host=$(hostname -f)
    if (( ${#host} > 64 )); then
      host=$(hostname -s)
    fi
    alt_names="$(ip addr | awk '/scope global/ {split($2,a,"/"); printf "IP:%s,", a[1]}')DNS:$(hostname -f),DNS:$(hostname -s)"

    log "Setting ${module_name} TLS certificate provisioning properties in $config_file"
    write_tls_settings "$config_file" "$module_name" "$host" "$alt_names"
  else
    log "Skipping ${module_name} TLS certificate provisioning properties in $config_file, already set"
  fi
}

# Main execution block - only runs when script is executed directly (not sourced)
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  if [[ "$1" == "setup" ]]; then
    if [[ -z "$2" ]]; then
      log "Error: module name required"
      usage
    fi
    setup_tls_config "$2"
  elif [[ $# -eq 4 ]]; then
    write_tls_settings "$1" "$2" "$3" "$4"
  else
    log "Error: Wrong number of arguments"
    usage
  fi
fi
