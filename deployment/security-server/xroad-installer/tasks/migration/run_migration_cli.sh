#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

XROAD_MIGRATION_UNATTENDED="${XROAD_MIGRATION_UNATTENDED:-}"

# This constant must match the destination set in download_migration_cli.sh.
MIGRATION_CLI_JAR="/var/tmp/migration-cli.jar"

# Sentinel directory for per-step completion tracking.
SENTINEL_DIR="/var/lib/xroad-upgrade"

ensure_sentinel_dir() {
  if [[ ! -d "$SENTINEL_DIR" ]]; then
    mkdir -p "$SENTINEL_DIR"
    chmod 750 "$SENTINEL_DIR"
    log_info "Created sentinel directory: $SENTINEL_DIR"
  fi
}

is_step_done() {
  [[ -f "$SENTINEL_DIR/step-${1}.done" ]]
}

mark_step_done() {
  touch "$SENTINEL_DIR/step-${1}.done"
  log_info "Sentinel written: $SENTINEL_DIR/step-${1}.done"
}

# Show a whiptail confirmation dialog before each migration step.
confirm_step() {
  local step="$1"
  local description="$2"

  if [[ "${XROAD_MIGRATION_UNATTENDED:-}" == "true" ]]; then
    log_info "Unattended mode: skipping confirmation for step '$step'"
    return 0
  fi

  if whiptail --title "Migration Step: $step" \
    --yesno "$description\n\nProceed?" 12 60; then
    log_info "Operator confirmed step: $step"
  else
    log_warn_exit "Migration step '$step' cancelled by operator"
  fi
}

# Run a single migration-CLI step with sentinel-based idempotency and
# per-step operator confirmation.
#
# Usage: run_migration_step <step> [arg1] [arg2] ...
run_migration_step() {
  local step="$1"
  shift
  local -a args=("$@")

  if is_step_done "$step"; then
    log_info "Step '$step' already completed (sentinel found) — skipping"
    return 0
  fi

  confirm_step "$step" "Run migration CLI step: $step"

  log_message "Running: java -jar $MIGRATION_CLI_JAR $step ${args[*]}"
  local tmpout
  tmpout=$(mktemp)
  if ! java -jar "$MIGRATION_CLI_JAR" "$step" "${args[@]}" 2>&1 | tee "$tmpout"; then
    rm -f "$tmpout"
    log_die "Migration CLI step '$step' failed (non-zero exit). Sentinel not written."
  fi
  # Migration-CLI sometimes returns 0 even after an internal exception. Scan
  # output for stack traces / error markers and fail if we see any.
  if grep -qE '^(Error |Caused by:|Exception in |[[:alpha:].]+Exception:)' "$tmpout"; then
    local firstline
    firstline=$(grep -m1 -E '^(Error |Caused by:|Exception in |[[:alpha:].]+Exception:)' "$tmpout")
    rm -f "$tmpout"
    log_die "Migration CLI step '$step' reported an error: $firstline"
  fi
  rm -f "$tmpout"

  mark_step_done "$step"
}

main() {
  log_message "================================="
  log_message "Step: Run Migration CLI"
  log_message "================================="
  log_message ""

  require_root

  if [[ ! -f "$MIGRATION_CLI_JAR" ]]; then
    log_die "Migration CLI JAR not found at $MIGRATION_CLI_JAR. Run download_migration_cli.sh first."
  fi

  ensure_sentinel_dir

  run_migration_step "validate"
  run_migration_step "config" "/etc/xroad/conf.d/local.ini" "/etc/xroad/conf.d/local.yaml"
  run_migration_step "keyconf" "/etc/xroad/signer" "/etc/xroad/db.properties"

  log_message ""
  log_info "Migration CLI steps completed."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
