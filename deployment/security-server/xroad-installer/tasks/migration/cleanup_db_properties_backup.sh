#!/bin/bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

source "$SCRIPT_DIR/../../lib/common.sh"

DB_PROPS_BACKUP="/etc/xroad/db.properties.bak"

XROAD_UPGRADE_UNATTENDED="${XROAD_UPGRADE_UNATTENDED:-}"
XROAD_DELETE_DB_PROPS_BAK="${XROAD_DELETE_DB_PROPS_BAK:-}"

cleanup_backup() {
  if [[ ! -f "$DB_PROPS_BACKUP" ]]; then
    log_info "$DB_PROPS_BACKUP not found — nothing to clean up"
    return 0
  fi

  # Explicit env-var override (works in interactive and unattended).
  if [[ "$XROAD_DELETE_DB_PROPS_BAK" == "yes" || "$XROAD_DELETE_DB_PROPS_BAK" == "true" ]]; then
    rm -f "$DB_PROPS_BACKUP"
    log_info "Deleted $DB_PROPS_BACKUP (XROAD_DELETE_DB_PROPS_BAK=$XROAD_DELETE_DB_PROPS_BAK)"
    return 0
  fi

  if [[ "$XROAD_DELETE_DB_PROPS_BAK" == "no" || "$XROAD_DELETE_DB_PROPS_BAK" == "false" ]]; then
    log_info "Keeping $DB_PROPS_BACKUP (XROAD_DELETE_DB_PROPS_BAK=$XROAD_DELETE_DB_PROPS_BAK)"
    return 0
  fi

  # Unattended without explicit choice: delete (no operator to confirm).
  # Set XROAD_DELETE_DB_PROPS_BAK=no in your config to opt out.
  if [[ "$XROAD_UPGRADE_UNATTENDED" == "true" ]]; then
    rm -f "$DB_PROPS_BACKUP"
    log_info "Unattended mode — deleted $DB_PROPS_BACKUP"
    return 0
  fi

  # Interactive: prompt the operator.
  if whiptail --title "Delete db.properties backup?" --yesno \
    "Upgrade completed.\n\nDelete the pre-upgrade backup at:\n  $DB_PROPS_BACKUP\n\nThe backup was created before db.properties keys were rewritten with the xroad.db.* prefix." 14 70; then
    rm -f "$DB_PROPS_BACKUP"
    log_info "Deleted $DB_PROPS_BACKUP"
  else
    log_info "Keeping $DB_PROPS_BACKUP — delete it manually once you have verified the upgrade"
  fi
}

main() {
  log_message "==============================="
  log_message "Step: Cleanup db.properties backup"
  log_message "==============================="
  log_message ""

  require_root

  cleanup_backup

  log_message ""
  log_info "db.properties backup cleanup completed."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
