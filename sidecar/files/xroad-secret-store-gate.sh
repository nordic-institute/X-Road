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
# The gate is fail-closed. It releases the gated services only when the
# embedded store is confirmed unsealed, or when XROAD_SECRET_STORE_HOST
# points at an external store this container neither unseals nor probes.
# Every other outcome — readiness timeout, seal status that cannot be
# determined, no usable unseal keys on disk, keys exhausted with the store
# still sealed — leaves the services stopped and exits nonzero. The
# program's autorestart=unexpected then re-runs the gate, so a store that
# becomes usable later is still picked up; until it does, the container
# health check keeps reporting unhealthy instead of the gated services
# crash-looping against a sealed store.
#
#############################################################################
set -eo pipefail

. /usr/share/xroad/scripts/sidecar/_openbao.sh

UNSEAL_KEYS_FILE=/etc/xroad/secret-store/unseal-keys
BAO_ADDR=https://127.0.0.1:8200
# Paces supervisord's restart of a failed gate; without it a fast-failing
# condition such as an absent unseal-keys file spins the program.
RETRY_DELAY_SECONDS=5

log() { echo "$(date --utc -Iseconds) INFO [secret-store-gate] $*"; }
warn() { echo "$(date --utc -Iseconds) WARN [secret-store-gate] $*" >&2; }

# Keeps the gated services stopped and hands supervisord a nonzero exit to
# retry on.
fail() {
  warn "$*; not starting X-Road services"
  sleep "$RETRY_DELAY_SECONDS"
  exit 1
}

release() {
  log "Starting X-Road services"
  supervisorctl start "xroad-services:*" || true
}

if [ -n "${XROAD_SECRET_STORE_HOST:-}" ]; then
  release
  exit 0
fi

if wait_until_ready "$BAO_ADDR" 60 1; then
  log "OpenBao is ready"
else
  fail "Timed out waiting for OpenBao to become ready"
fi

# The embedded store is unusable without these keys: the OpenBao process is
# sealed from the moment it starts and only they can unseal it.
if [ ! -s "$UNSEAL_KEYS_FILE" ]; then
  fail "No unseal keys in $UNSEAL_KEYS_FILE, cannot unseal the embedded OpenBao"
fi

# is_sealed: 0 = sealed, 1 = unsealed, 2 = status could not be determined.
sealed_rc=0
is_sealed "$BAO_ADDR" || sealed_rc=$?
case "$sealed_rc" in
  1)
    log "OpenBao is already unsealed"
    ;;
  2)
    fail "Cannot determine OpenBao seal status"
    ;;
  *)
    log "Unsealing OpenBao..."
    while IFS= read -r key || [ -n "$key" ]; do
      # A key that does not belong to this store is rejected; the seal
      # status below, not this call, decides whether to keep going.
      unseal "$BAO_ADDR" "$key" || true
      sealed_rc=0
      is_sealed "$BAO_ADDR" || sealed_rc=$?
      case "$sealed_rc" in
        1) break ;;
        2) fail "Cannot determine OpenBao seal status while unsealing" ;;
      esac
    done <"$UNSEAL_KEYS_FILE"

    if [ "$sealed_rc" -ne 1 ]; then
      fail "Exhausted the unseal keys in $UNSEAL_KEYS_FILE with OpenBao still sealed"
    fi
    log "Successfully unsealed OpenBao"
    ;;
esac

release
