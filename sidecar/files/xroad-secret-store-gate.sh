#!/bin/bash
#############################################################################
#
# Supervisord command for the xroad-secret-store-gate program.
#
# Supervisord starts every program independently with no dependency
# ordering, unlike the native package's systemd units (xroad-signer.service
# etc. declare After=/Wants= on the secret-store service). This program
# stands in for that ordering: the X-Road service programs it gates
# (see the xroad-services group) ship with autostart=false, and this is the
# only thing that starts them, once the secret store is actually usable.
#
# A freshly (re)started embedded OpenBao process is always sealed even
# though its PostgreSQL-backed storage is already initialized — unseal
# state lives in the process, not the storage. This unseals it, using the
# same persisted keys /usr/share/xroad/scripts/sidecar/secret-store-init.sh
# wrote on first boot, before releasing the gate.
#
#############################################################################
set -eo pipefail

. /usr/share/xroad/scripts/sidecar/_openbao.sh

UNSEAL_KEYS_FILE=/etc/xroad/secret-store/unseal-keys
BAO_ADDR=https://127.0.0.1:8200

log() { echo "$(date --utc -Iseconds) INFO [secret-store-gate] $*"; }
warn() { echo "$(date --utc -Iseconds) WARN [secret-store-gate] $*" >&2; }

if [ -z "${XROAD_SECRET_STORE_HOST:-}" ]; then
  if wait_until_ready "$BAO_ADDR" 60 1; then
    log "OpenBao is ready"
  else
    warn "Timed out waiting for OpenBao; starting X-Road services anyway"
  fi

  if [ -f "$UNSEAL_KEYS_FILE" ]; then
    sealed_rc=0
    is_sealed "$BAO_ADDR" || sealed_rc=$?
    if [ "$sealed_rc" -eq 2 ]; then
      warn "Cannot determine OpenBao seal status; starting X-Road services anyway"
    elif [ "$sealed_rc" -eq 0 ]; then
      log "Unsealing OpenBao..."
      while IFS= read -r key || [ -n "$key" ]; do
        unseal "$BAO_ADDR" "$key" || true
        sealed_rc=0
        is_sealed "$BAO_ADDR" || sealed_rc=$?
        if [ "$sealed_rc" -ne 0 ]; then
          log "Successfully unsealed OpenBao"
          break
        fi
      done <"$UNSEAL_KEYS_FILE"
    fi
  fi
fi

log "Starting X-Road services"
supervisorctl start "xroad-services:*" || true
