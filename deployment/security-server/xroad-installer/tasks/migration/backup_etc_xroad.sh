#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../lib/common.sh"

XROAD_CONF_DIR="/etc/xroad"
BACKUP_PREFIX="xroad-pre-v8-backup"

backup_etc_xroad() {
  if [[ ! -d "$XROAD_CONF_DIR" ]]; then
    log_die "$XROAD_CONF_DIR not found — nothing to back up. Refusing to continue upgrade."
  fi

  # Honour an existing backup if one is already present in /etc/xroad to avoid
  # overwriting the earliest pre-upgrade snapshot on a resumed run.
  local existing
  existing=$(find "$XROAD_CONF_DIR" -maxdepth 1 -type f -name "${BACKUP_PREFIX}-*.tar.gz" -print -quit 2>/dev/null || true)
  if [[ -n "$existing" ]]; then
    log_info "Pre-upgrade backup already exists: $existing — skipping"
    return 0
  fi

  local ts
  ts=$(date '+%Y-%m-%d_%H%M%S')
  local archive_name="${BACKUP_PREFIX}-${ts}.tar.gz"
  local final_path="${XROAD_CONF_DIR}/${archive_name}"
  # Stage outside $XROAD_CONF_DIR so tar never sees its own output appearing
  # inside the directory it is archiving — otherwise GNU tar reports
  # "<dir>: file changed as we read it" and exits 1.
  local staging_dir="${TMPDIR:-/var/tmp}"
  local staging_path="${staging_dir}/${archive_name}"

  log_info "Creating pre-upgrade backup of $XROAD_CONF_DIR -> $final_path"

  # --exclude is kept as a defensive belt: if an unexpected *.tar.gz is left in
  # /etc/xroad (e.g. operator-placed), tar won't try to read it back.
  if tar -czf "$staging_path" \
        --exclude="${BACKUP_PREFIX}-*.tar.gz" \
        -C "$(dirname "$XROAD_CONF_DIR")" \
        "$(basename "$XROAD_CONF_DIR")"; then
    chmod 600 "$staging_path"
    if ! mv "$staging_path" "$final_path"; then
      rm -f "$staging_path"
      log_die "Failed to move backup into $final_path. Aborting upgrade — no further changes have been made."
    fi
    log_info "Backup written: $final_path"
  else
    rm -f "$staging_path"
    log_die "Failed to create backup at $final_path. Aborting upgrade — no further changes have been made."
  fi
}

main() {
  log_message "==============================="
  log_message "Step: Backup /etc/xroad"
  log_message "==============================="
  log_message ""

  require_root
  backup_etc_xroad

  log_message ""
  log_info "/etc/xroad backup completed."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
