#!/bin/bash

set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions and logging
source "$SCRIPT_DIR/../lib/common.sh"

CONFIG_FILE="/etc/xroad/conf.d/local-tls.yaml"
YAML_HELPER="$SCRIPT_DIR/../lib/yaml_helper.sh"

# Function to split IP and DNS entries from comma-separated list
split_ip_dns() {
  local input="$1"
  local ip_list dns_list
  ip_list=$(echo "$input" | tr ',' '\n' | grep '^IP:' | cut -d: -f2 | paste -sd,)
  dns_list=$(echo "$input" | tr ',' '\n' | grep '^DNS:' | cut -d: -f2 | paste -sd,)
  echo "$ip_list" "$dns_list"
}

# Function to get YAML value
get_prop() {
  "$YAML_HELPER" get "$1" "$2" 2>/dev/null
}

# Function to configure TLS settings for a service
configure_service_tls() {
  local service="$1"
  local cn="$2"
  local altn="$3"

  log_message "Configuring TLS settings for $service"

  # Check if settings already exist
  local existing_cn existing_alt_names existing_ip_alt_names
  existing_cn=$(get_prop "$CONFIG_FILE" "xroad.${service}.tls.certificate-provisioning.common-name")
  existing_alt_names=$(get_prop "$CONFIG_FILE" "xroad.${service}.tls.certificate-provisioning.alt-names")
  existing_ip_alt_names=$(get_prop "$CONFIG_FILE" "xroad.${service}.tls.certificate-provisioning.ip-subject-alt-names")

  if [[ -n "$existing_cn" || -n "$existing_alt_names" || -n "$existing_ip_alt_names" ]]; then
    log_info "$service TLS settings already exist, skipping"
    return
  fi

  # Split IP and DNS entries
  local ip_list dns_list
  read ip_list dns_list < <(split_ip_dns "$altn")

  log_message "  Common Name: $cn"
  log_message "  DNS Alternative Names: $dns_list"
  log_message "  IP Alternative Names: $ip_list"

  # Write settings using yaml_helper
  "$YAML_HELPER" set "$CONFIG_FILE" "xroad.${service}.tls.certificate-provisioning.common-name" "$cn"
  "$YAML_HELPER" set "$CONFIG_FILE" "xroad.${service}.tls.certificate-provisioning.alt-names" "$dns_list"
  "$YAML_HELPER" set "$CONFIG_FILE" "xroad.${service}.tls.certificate-provisioning.ip-subject-alt-names" "$ip_list"

  log_info "TLS settings configured for $service"
}

main() {
  log_message "================================"
  log_message "Configuring TLS Settings"
  log_message "================================"
  log_message ""

  # Check if running as root
  require_root

  # Ensure the configuration directory exists
  local conf_dir
  conf_dir=$(dirname "$CONFIG_FILE")
  if [ ! -d "$conf_dir" ]; then
    log_message "Creating configuration directory: $conf_dir"
    mkdir -p "$conf_dir"
  fi

  # Get TLS settings from environment variables
  local tls_hostname="${XROAD_TLS_HOSTNAME:-}"
  local tls_alt_names="${XROAD_TLS_ALT_NAMES:-}"

  if [ -z "$tls_hostname" ] || [ -z "$tls_alt_names" ]; then
    log_die "TLS settings not provided. XROAD_TLS_HOSTNAME and XROAD_TLS_ALT_NAMES are required."
  fi

  log_message "TLS Hostname: $tls_hostname"
  log_message "TLS Alternative Names: $tls_alt_names"
  log_message ""

  # Configure TLS settings for proxy
  configure_service_tls "proxy" "$tls_hostname" "$tls_alt_names"
  log_message ""

  # Configure TLS settings for proxy-ui-api
  configure_service_tls "proxy-ui-api" "$tls_hostname" "$tls_alt_names"
  log_message ""

  log_message "TLS configuration file created: $CONFIG_FILE"
  log_message ""
  log_message "================================"
  log_info "TLS configuration completed successfully!"
  log_message "================================"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main
fi
