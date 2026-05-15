#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../../lib/common.sh"

XROAD_UPGRADE_UNATTENDED="${XROAD_UPGRADE_UNATTENDED:-}"
XROAD_DELETE_OBSOLETE_FILES="${XROAD_DELETE_OBSOLETE_FILES:-}"

# Obsolete V7 paths (files, dirs, globs) that V8 no longer reads.
# Globs and literal paths are both resolved via `compgen -G` at deletion time;
# directories vs files are discriminated at deletion time with `[[ -d ]]`.
OBSOLETE_PATHS=(
  "/etc/xroad/conf.d/*.ini"
  "/etc/xroad/devices.ini"
  "/etc/xroad/configuration-anchor.xml"
  "/etc/xroad/signer"
  "/etc/xroad/conf.d/*-logback.xml"
)

# Resolve OBSOLETE_PATHS into concrete existing paths (no-match returns empty).
resolve_obsolete_paths() {
  local results=()
  local pattern match
  for pattern in "${OBSOLETE_PATHS[@]}"; do
    while IFS= read -r match; do
      [[ -n "$match" ]] && results+=("$match")
    done < <(compgen -G "$pattern" || true)
  done
  printf '%s\n' "${results[@]}"
}

delete_obsolete() {
  local resolved
  mapfile -t resolved < <(resolve_obsolete_paths)

  if [[ ${#resolved[@]} -eq 0 ]]; then
    log_info "No obsolete V7 config files found — nothing to clean up"
    return 0
  fi

  local p
  for p in "${resolved[@]}"; do
    if [[ -d "$p" ]]; then
      rm -rf "$p"
      log_info "Deleted obsolete directory: $p"
    else
      rm -f "$p"
      log_info "Deleted obsolete file: $p"
    fi
  done
}

format_paths_for_prompt() {
  local p
  for p in "${OBSOLETE_PATHS[@]}"; do
    if [[ -d "$p" ]]; then
      printf '  %s  (directory)\n' "$p"
    else
      printf '  %s\n' "$p"
    fi
  done
}

cleanup_obsolete() {
  # Explicit env-var override (works in interactive and unattended).
  if [[ "$XROAD_DELETE_OBSOLETE_FILES" == "yes" || "$XROAD_DELETE_OBSOLETE_FILES" == "true" ]]; then
    delete_obsolete
    return 0
  fi

  if [[ "$XROAD_DELETE_OBSOLETE_FILES" == "no" || "$XROAD_DELETE_OBSOLETE_FILES" == "false" ]]; then
    log_info "Keeping obsolete V7 config files (XROAD_DELETE_OBSOLETE_FILES=$XROAD_DELETE_OBSOLETE_FILES)"
    return 0
  fi

  # Unattended without explicit choice: delete (no operator to confirm).
  # Set XROAD_DELETE_OBSOLETE_FILES=no in your config to opt out.
  if [[ "$XROAD_UPGRADE_UNATTENDED" == "true" ]]; then
    log_info "Unattended mode — deleting obsolete V7 config files"
    delete_obsolete
    return 0
  fi

  # Interactive: prompt the operator with the exact list.
  local prompt
  prompt="$(cat <<EOF
Upgrade to X-Road 8.0 completed.

The following V7 configuration files are no longer used by V8 and can be deleted:

$(format_paths_for_prompt)

Delete these obsolete files now?
EOF
)"

  if whiptail --title "Delete obsolete V7 config files?" --yesno "$prompt" 18 78; then
    delete_obsolete
  else
    log_info "Keeping obsolete V7 config files — remove them manually once you have verified the upgrade"
  fi
}

main() {
  log_message "==============================="
  log_message "Step: Cleanup obsolete V7 config files"
  log_message "==============================="
  log_message ""

  require_root
  cleanup_obsolete

  log_message ""
  log_info "Obsolete files cleanup completed."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then main; fi
