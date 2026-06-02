#!/bin/bash

set -euo pipefail

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions and logging
source "$SCRIPT_DIR/../lib/common.sh"

CONFIG_FILE="/etc/xroad/conf.d/local.yaml"
YAML_HELPER="$SCRIPT_DIR/../lib/yaml_helper.sh"
PROPERTY="xroad.proxy.message-log.enabled"

# Environment variables with defaults (message log is enabled by default)
XROAD_MESSAGELOG_ENABLED="${XROAD_MESSAGELOG_ENABLED:-true}"

main() {
  log_message "================================"
  log_message "Configuring Message Log"
  log_message "================================"
  log_message ""

  # Check if running as root
  require_root

  # Normalise the requested value
  case "${XROAD_MESSAGELOG_ENABLED,,}" in
    true|yes|1|on)   XROAD_MESSAGELOG_ENABLED="true" ;;
    false|no|0|off)  XROAD_MESSAGELOG_ENABLED="false" ;;
    *) log_die "Invalid value for XROAD_MESSAGELOG_ENABLED: '$XROAD_MESSAGELOG_ENABLED' (expected true/false)" ;;
  esac

  # Ensure the configuration directory exists
  local conf_dir
  conf_dir=$(dirname "$CONFIG_FILE")
  if [[ ! -d "$conf_dir" ]]; then
    log_message "Creating configuration directory: $conf_dir"
    mkdir -p "$conf_dir"
  fi

  log_message "Setting $PROPERTY = $XROAD_MESSAGELOG_ENABLED"
  "$YAML_HELPER" set "$CONFIG_FILE" "$PROPERTY" "$XROAD_MESSAGELOG_ENABLED"

  if [[ "$XROAD_MESSAGELOG_ENABLED" == "true" ]]; then
    log_info "Message log enabled"
  else
    log_warn "Message log disabled"
  fi

  log_message ""
  log_message "================================"
  log_info "Message log configuration completed successfully!"
  log_message "================================"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main
fi
