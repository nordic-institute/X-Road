#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../lib/common.sh"

DB_PROPS="/etc/xroad/db.properties"

migrate_db_properties() {
  [[ -f "$DB_PROPS" ]] || { log_warn "$DB_PROPS not found, skipping migration"; return 0; }

  local tmp
  tmp=$(mktemp)

  # Prefix every key = value line with xroad.db. unless already prefixed.
  # Comments, blank lines, and prefixed keys pass through untouched.
  sed -E '/^[[:space:]]*([#!]|xroad\.db\.|$)/b; s/^([[:space:]]*)([^=[:space:]]+)([[:space:]]*=)/\1xroad.db.\2\3/' \
    "$DB_PROPS" > "$tmp"

  if cmp -s "$DB_PROPS" "$tmp"; then
    log_info "$DB_PROPS already migrated, nothing to do"
  else
    cp -p "$DB_PROPS" "${DB_PROPS}.bak"
    cat "$tmp" > "$DB_PROPS"   # in-place to preserve perms/ownership
    log_info "Migrated $DB_PROPS to xroad.db.* format (backup: ${DB_PROPS}.bak)"
  fi

  rm -f "$tmp"
}

main() {
  log_message "=== Migrate db.properties ==="
  require_root
  migrate_db_properties
  log_info "Migration completed."
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main