#!/bin/bash
# Restore one or more LXD containers from a named snapshot (empty|custom).
# Stops each container, restores, then starts it again.
#
# Usage:
#   restore-containers.sh --name=empty
#   restore-containers.sh --name=custom --containers="xrd-ss0 xrd-cs"
set -euo pipefail

source "${BASH_SOURCE%/*}/../../../scripts/lib/base-script.sh"

DEFAULT_CONTAINERS="xrd-ss0 xrd-ss1 xrd-cs"
SNAPSHOT_NAME=""
CONTAINERS="$DEFAULT_CONTAINERS"

# --- argument parsing ---
for arg in "$@"; do
  case "$arg" in
    --name=*)
      SNAPSHOT_NAME="${arg#--name=}"
      ;;
    --containers=*)
      CONTAINERS="${arg#--containers=}"
      ;;
    *)
      log_error "Unknown argument: $arg"
      exit 1
      ;;
  esac
done

if [[ -z "$SNAPSHOT_NAME" ]]; then
  log_error "--name is required (allowed values: empty, custom)"
  exit 1
fi

if [[ "$SNAPSHOT_NAME" != "empty" && "$SNAPSHOT_NAME" != "custom" ]]; then
  log_error "Invalid --name value: '$SNAPSHOT_NAME' (allowed: empty, custom)"
  exit 1
fi

# --- pre-flight: verify all containers and snapshots exist before touching anything ---
for container in $CONTAINERS; do
  if ! lxc info "$container" &>/dev/null; then
    log_error "Container '$container' does not exist"
    exit 1
  fi

  if ! lxc query "/1.0/instances/${container}/snapshots/${SNAPSHOT_NAME}" &>/dev/null; then
    log_error "Snapshot '$SNAPSHOT_NAME' not found on container '$container'"
    exit 1
  fi
done

# --- restore loop ---
RESTORED=""
for container in $CONTAINERS; do
  log_info "Restoring $container from snapshot '$SNAPSHOT_NAME'"

  # Stop if running
  STATUS=$(lxc info "$container" | awk '/^Status:/ {print tolower($2)}')
  if [[ "$STATUS" == "running" ]]; then
    log_info "  Stopping $container"
    if ! lxc stop "$container"; then
      log_error "Failed to stop $container"
      exit 1
    fi
  fi

  # Restore
  log_info "  Running: lxc restore $container $SNAPSHOT_NAME"
  if ! lxc restore "$container" "$SNAPSHOT_NAME"; then
    log_error "lxc restore $container $SNAPSHOT_NAME failed"
    exit 1
  fi

  # Start
  log_info "  Starting $container"
  if ! lxc start "$container"; then
    log_error "Failed to start $container after restore"
    exit 1
  fi

  RESTORED="$RESTORED $container"
done

log_info "Restore complete — snapshot '$SNAPSHOT_NAME' applied to:$RESTORED"
