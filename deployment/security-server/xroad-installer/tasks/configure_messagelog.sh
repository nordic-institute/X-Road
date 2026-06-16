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

  # Normalise the requested value (safe to call when already normalised)
  normalize_bool XROAD_MESSAGELOG_ENABLED

  if [[ ! -x "$YAML_HELPER" ]]; then
    log_die "YAML helper not found or not executable: $YAML_HELPER"
  fi

  # Ensure the configuration directory exists (the xroad package is not installed yet)
  local conf_dir
  conf_dir=$(dirname "$CONFIG_FILE")
  if [[ ! -d "$conf_dir" ]]; then
    log_message "Creating configuration directory: $conf_dir"
    mkdir -p "$conf_dir"
  fi

  log_message "Setting $PROPERTY = $XROAD_MESSAGELOG_ENABLED"
  "$YAML_HELPER" set "$CONFIG_FILE" "$PROPERTY" "$XROAD_MESSAGELOG_ENABLED"

  log_message ""
  log_message "================================"
  log_info "Message log configuration completed successfully!"
  log_message "================================"
}

# Run main function if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main
fi
