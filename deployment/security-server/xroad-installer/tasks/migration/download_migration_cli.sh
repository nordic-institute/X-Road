#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../lib/common.sh"

# Required: Artifactory download URL for the migration-cli fat JAR.
# Example: export XROAD_MIGRATION_CLI_URL=https://artifactory.example.com/migration-cli-1.0.0.jar
XROAD_MIGRATION_CLI_URL="${XROAD_MIGRATION_CLI_URL:-}"

# run_migration_cli.sh must reference the same path.
MIGRATION_CLI_JAR="/var/tmp/migration-cli.jar"

download_jar() {
  if [[ -z "${XROAD_MIGRATION_CLI_URL:-}" ]]; then
    log_die "XROAD_MIGRATION_CLI_URL is required. Set it to the Artifactory download URL for migration-cli.jar."
  fi

  log_message "Downloading migration-cli JAR..."
  log_message "URL: $XROAD_MIGRATION_CLI_URL"
  log_message "Destination: $MIGRATION_CLI_JAR"

  if command -v curl >/dev/null 2>&1; then
    if curl -fsSL "$XROAD_MIGRATION_CLI_URL" -o "$MIGRATION_CLI_JAR"; then
      log_info "Downloaded migration-cli.jar via curl"
    else
      log_die "curl failed to download migration-cli JAR from $XROAD_MIGRATION_CLI_URL"
    fi
  elif command -v wget >/dev/null 2>&1; then
    if wget -qO "$MIGRATION_CLI_JAR" "$XROAD_MIGRATION_CLI_URL"; then
      log_info "Downloaded migration-cli.jar via wget"
    else
      log_die "wget failed to download migration-cli JAR from $XROAD_MIGRATION_CLI_URL"
    fi
  else
    log_die "Neither curl nor wget is available. Install one to proceed."
  fi

  if [[ ! -f "$MIGRATION_CLI_JAR" ]]; then
    log_die "JAR file not found at $MIGRATION_CLI_JAR after download"
  fi

  chmod 600 "$MIGRATION_CLI_JAR"
  log_info "migration-cli.jar permissions set to 600"
}

main() {
  log_message "==============================="
  log_message "Step: Download Migration CLI"
  log_message "==============================="
  log_message ""

  require_root

  download_jar

  log_message ""
  log_info "Migration CLI downloaded to $MIGRATION_CLI_JAR"
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
