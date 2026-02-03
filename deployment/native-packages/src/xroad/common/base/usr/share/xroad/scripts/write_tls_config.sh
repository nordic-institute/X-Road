#!/bin/bash
#
# Script for writing TLS certificate provisioning configuration to YAML files.
#
# This script provides functions to write TLS settings for X-Road modules.
# It handles splitting IP and DNS Subject Alternative Names (SANs) and writes
# the appropriate configuration values.
#
# Usage (when called directly):
#   write_tls_config.sh <config_file> <module_name> <common_name> <alt_names>
#
# Usage (when sourced):
#   . write_tls_config.sh
#   write_tls_settings "$CONFIG_FILE" "proxy" "$cn" "$altn"
#

log () { echo >&2 "$@"; }

usage() {
  cat >&2 <<EOF
Usage: $0 <config_file> <module_name> <common_name> <alt_names>

Arguments:
  config_file   - Path to the YAML configuration file (e.g., /etc/xroad/conf.d/local-tls.yaml)
  module_name   - X-Road module name (e.g., proxy, op-monitor, proxy-ui-api)
  common_name   - Common Name (CN) for the TLS certificate
  alt_names     - Alternative names in format: IP:1.1.1.1,DNS:name,IP:2.2.2.2,...

Examples:
  $0 /etc/xroad/conf.d/local-tls.yaml proxy host.example.com "IP:10.0.0.1,DNS:host.example.com"
  $0 /etc/xroad/conf.d/local-tls.yaml op-monitor server.local "IP:192.168.1.1,IP:192.168.1.2,DNS:server.local"

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
#
# Example:
#   write_tls_settings "/etc/xroad/conf.d/local-tls.yaml" "proxy" "host.example.com" "IP:10.0.0.1,DNS:host.example.com"
#
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

# Main execution block - only runs when script is executed directly (not sourced)
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  # Check if correct number of arguments provided
  if [ $# -ne 4 ]; then
    log "Error: Wrong number of arguments"
    usage
  fi

  # Call the function with provided arguments
  write_tls_settings "$1" "$2" "$3" "$4"
fi
