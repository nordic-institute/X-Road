#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

TIMESTAMP=$(date '+%Y-%m-%d_%H%M%S')

XROAD_INSTALLER_LOG_FILE="xroad-upgrade-${TIMESTAMP}.log"
export XROAD_INSTALLER_LOG_FILE

source "$SCRIPT_DIR/lib/common.sh"

XROAD_UPGRADE_CONFIG_FILE="${XROAD_UPGRADE_CONFIG_FILE:-}"
XROAD_UPGRADE_UNATTENDED="${XROAD_UPGRADE_UNATTENDED:-}"
XROAD_UPGRADE_CONFIRMED="${XROAD_UPGRADE_CONFIRMED:-}"

XROAD_MIGRATION_UNATTENDED="${XROAD_MIGRATION_UNATTENDED:-}"
XROAD_MIGRATION_CLI_URL="${XROAD_MIGRATION_CLI_URL:-}"

XROAD_REPO_BASE_URL="${XROAD_REPO_BASE_URL:-}"
XROAD_REPO_MAIN="${XROAD_REPO_MAIN:-}"
XROAD_REPO_GPG_KEY_URL="${XROAD_REPO_GPG_KEY_URL:-}"
XROAD_REPO_URL_OVERRIDE="${XROAD_REPO_URL_OVERRIDE:-}"

OPENBAO_MIRROR="${OPENBAO_MIRROR:-}"
OPENBAO_MIRROR_USER="${OPENBAO_MIRROR_USER:-}"

XROAD_SS_PACKAGE="${XROAD_SS_PACKAGE:-}"

XROAD_DELETE_OBSOLETE_FILES="${XROAD_DELETE_OBSOLETE_FILES:-}"

parse_args() {
  while [[ $# -gt 0 ]]; do
    case $1 in
      --config-file)
          if [[ $# -lt 2 ]]; then
              log_die "Missing path argument for --config-file"
          fi
          XROAD_UPGRADE_CONFIG_FILE="$2"
          shift 2
          ;;
      -h|--help)
          show_help
          exit 0
          ;;
      *)
          log_die "Unknown option: $1"
          ;;
    esac
  done
}

show_help() {
    cat << EOF
Usage: $(basename "$0") [OPTIONS]

X-Road Security Server Upgrade Wizard (7.8.x -> 8.0)

OPTIONS:
    --config-file FILE          Path to a configuration file containing upgrade settings
                                (see xroad-upgrade.conf.sample for available variables)
    -h, --help                  Show this help message

EXAMPLES:
    sudo ./xroad-upgrade.sh
    sudo ./xroad-upgrade.sh --config-file /path/to/xroad-upgrade.conf

    # Unattended (Ansible) — set XROAD_UPGRADE_UNATTENDED=true in the config file or env.
    sudo XROAD_UPGRADE_UNATTENDED=true ./xroad-upgrade.sh --config-file xroad-upgrade.conf

EOF
}

# Args:
#   $1 — task script name under tasks/migration/ (e.g. "check_version_gate.sh")
#   $2 — success log message (printed via log_info after successful completion)
#   $3 — failure recovery hint (shown in whiptail msgbox or passed to log_die)
run_step() {
  local task="$1"
  local success_msg="$2"
  local failure_hint="$3"
  local task_path="$SCRIPT_DIR/tasks/migration/$task"

  if [[ ! -f "$task_path" ]]; then
    log_die "Upgrade task not found: $task_path"
  fi

  if bash "$task_path"; then
    log_info "$success_msg"
  else
    if [[ "${XROAD_UPGRADE_UNATTENDED:-}" == "true" ]]; then
      log_die "$failure_hint"
    else
      whiptail --msgbox "$failure_hint" 12 78 --title "Upgrade Failed"
      exit $EXIT_ERROR
    fi
  fi
  log_message ""
}

main() {
  parse_args "$@"

  # Load configuration file if provided (xroad-installer.sh pattern exactly).
  if [[ -n "$XROAD_UPGRADE_CONFIG_FILE" ]]; then
    if [[ -f "$XROAD_UPGRADE_CONFIG_FILE" ]]; then
      log_info "Loading configuration from: $XROAD_UPGRADE_CONFIG_FILE"
      set +u
      set -a
      source "$XROAD_UPGRADE_CONFIG_FILE"
      set +a
      set -u
    else
      log_die "Configuration file not found: $XROAD_UPGRADE_CONFIG_FILE"
    fi
  fi

  # Banner
  log_message "========================================"
  log_message "  X-Road Security Server Upgrade Wizard"
  log_message "========================================"
  log_message ""

  require_root

  if [[ "${XROAD_UPGRADE_UNATTENDED:-}" == "true" ]]; then
    log_info "Unattended mode enabled — bypassing interactive prompts across all steps"
    XROAD_UPGRADE_CONFIRMED=yes
    XROAD_MIGRATION_UNATTENDED=true
    export XROAD_UPGRADE_CONFIRMED
    export XROAD_MIGRATION_UNATTENDED
  fi

  export XROAD_UPGRADE_CONFIRMED
  export XROAD_MIGRATION_UNATTENDED
  export XROAD_MIGRATION_CLI_URL
  export XROAD_REPO_BASE_URL
  export XROAD_REPO_MAIN
  export XROAD_REPO_GPG_KEY_URL
  export XROAD_REPO_URL_OVERRIDE
  export OPENBAO_MIRROR
  export OPENBAO_MIRROR_USER
  export XROAD_SS_PACKAGE
  export XROAD_DELETE_OBSOLETE_FILES

  # Step 1: Version gate — fail fast if not 7.8.x.
  run_step "check_version_gate.sh" \
    "Version gate passed" \
    "Pre-flight check failed. No changes made. Fix the issue and re-run the wizard."

  # Step 2: Snapshot /etc/xroad to a timestamped tar.gz inside /etc/xroad — must run before the first on-disk mutation (Step 3).
  run_step "backup_etc_xroad.sh" \
    "/etc/xroad backup created" \
    "Backup of /etc/xroad failed. No further changes have been made. Free up disk space or fix permissions on /etc/xroad and re-run the wizard."

  # Step 3: Migrate db.properties to V8 format — add xroad.db.* prefix, backup original.
  run_step "migrate_db_properties.sh" \
    "db.properties migrated to V8 format" \
    "db.properties migration failed. Original file is preserved at /etc/xroad/db.properties.bak (if a backup was written). Restore it and re-run the wizard."

  # Step 4: PostgreSQL pre-flight — parse db.properties and verify PG >= 15.
  run_step "check_pg_preflight.sh" \
    "PostgreSQL pre-flight passed" \
    "Pre-flight check failed. No changes made. Fix the issue and re-run the wizard."

  # Step 5: OpenBao repository setup — required before stopping services.
  run_step "setup_openbao_repo.sh" \
    "OpenBao repository configured" \
    "OpenBao repository setup failed. No destructive steps executed. Check network and OPENBAO_MIRROR settings."

  # Step 6: Download migration-CLI JAR — must exist before stopping services.
  run_step "download_migration_cli.sh" \
    "Migration-CLI downloaded" \
    "Migration CLI download failed. No services stopped yet. Check XROAD_MIGRATION_CLI_URL and network connectivity."

  # Step 7: Stop X-Road services — dynamic discovery, polling wait.
  run_step "stop_xroad_services.sh" \
    "X-Road services stopped" \
    "Service stop failed. Some services may still be running. Check: systemctl status xroad-proxy xroad-signer"

  # Step 8: Switch to V8 repository — backup V7 repo file, write V8 repo.
  run_step "switch_v8_repository.sh" \
    "V8 repository activated" \
    "Repository switch failed. Services are stopped. Restore V7 repo from: /etc/apt/sources.list.d/xroad.list.v7.bak.* (DEB) or /etc/yum.repos.d/ (RPM)"

  # Step 9: Upgrade packages — apt-get install / yum update -y for xroad-securityserver.
  run_step "upgrade_packages.sh" \
    "Security Server packages upgraded to 8.0" \
    "Package upgrade failed. V8 repo is active but packages are still at V7. Restore V7 repo backup and investigate before retrying."

  # Step 10: Migrate V7 on-disk TLS certificates/keys to the local secret store (KV xrd-secret/tls/*).
  run_step "migrate_tls_to_secret_store.sh" \
    "TLS certificates migrated to secret store" \
    "TLS-to-secret-store migration failed. X-Road services are still stopped. Verify xroad-secret-store-local is installed and OpenBao is reachable, then re-run the wizard."

  # Step 11: Run migration-CLI subcommands — per-step confirmation + sentinel resumability.
  run_step "run_migration_cli.sh" \
    "Migration-CLI steps completed" \
    "Migration failed. X-Road services are stopped. V7 repo is still active. Fix the issue and re-run the wizard to resume from checkpoint."

  # Step 12: Start X-Road services, is-active polling.
  run_step "start_xroad_services.sh" \
    "X-Road services started" \
    "Service start failed after successful upgrade. Run: systemctl status <service> to diagnose."

  # Step 13: Remove obsolete V7 config files (operator-confirmed; unattended deletes by default — set XROAD_DELETE_OBSOLETE_FILES=no to keep).
  run_step "cleanup_obsolete_files.sh" \
    "Obsolete V7 config files cleanup completed" \
    "Obsolete files cleanup step failed. Upgrade succeeded; review /etc/xroad/conf.d and /etc/xroad/signer manually."

  log_message "========================================"
  log_info "X-Road 8.0 upgrade completed successfully!"
  log_message "========================================"
  log_message ""
  log_message "Next steps:"
  log_message "  - Review the log file: $XROAD_INSTALLER_LOG_FILE"
  log_message "  - Verify services: systemctl list-units 'xroad-*'"
  log_message ""
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main "$@"
fi
