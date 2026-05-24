#!/bin/bash
# snapshot-containers.sh — take a named LXD snapshot of xrd-* containers.
#
# Usage:
#   snapshot-containers.sh [--name=empty|custom] [--containers="xrd-a xrd-b ..."]
#
# Defaults:
#   --name        custom
#   --containers  xrd-ss0 xrd-ss1 xrd-cs
#
# Snapshot names are restricted to: empty, custom.
# If a snapshot with the same name already exists it is deleted first.
# The container is stopped before snapshotting and restored to its prior
# RUNNING/STOPPED state afterwards.
set -e

source "${BASH_SOURCE%/*}/../../../.scripts/base-script.sh"

# ---------- defaults ---------------------------------------------------------
SNAPSHOT_NAME="custom"
CONTAINERS="xrd-ss0 xrd-ss1 xrd-cs"

# ---------- arg parsing ------------------------------------------------------
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

# ---------- validate name ----------------------------------------------------
if [[ "$SNAPSHOT_NAME" != "empty" && "$SNAPSHOT_NAME" != "custom" ]]; then
  log_error "Invalid --name '${SNAPSHOT_NAME}'. Allowed values: empty, custom."
  exit 1
fi

log_info "snapshot-containers: name=${SNAPSHOT_NAME} containers=${CONTAINERS}"

# ---------- snapshot loop ----------------------------------------------------
snapshotted=()

for container in $CONTAINERS; do
  # Verify container exists
  if ! lxc info "$container" >/dev/null 2>&1; then
    log_error "Container '${container}' does not exist."
    exit 1
  fi

  # Capture prior state (RUNNING or STOPPED)
  prior_state=$(lxc info "$container" | awk '/^Status:/ {print $2}')
  log_info "${container}: prior state: ${prior_state}"

  # Stop if running
  if [[ "$prior_state" == "RUNNING" ]]; then
    log_info "${container}: stopping"
    lxc stop "$container"
  fi

  # Delete existing snapshot if present
  if lxc query "/1.0/instances/${container}/snapshots/${SNAPSHOT_NAME}" &>/dev/null; then
    log_info "${container}: deleting existing snapshot '${SNAPSHOT_NAME}'"
    lxc delete "${container}/${SNAPSHOT_NAME}"
  fi

  # Take snapshot
  log_info "${container}: creating snapshot '${SNAPSHOT_NAME}'"
  lxc snapshot "$container" "$SNAPSHOT_NAME"

  # Restore prior state
  if [[ "$prior_state" == "RUNNING" ]]; then
    log_info "${container}: starting"
    lxc start "$container"
  fi

  snapshotted+=("$container")
done

log_info "Done. snapshot='${SNAPSHOT_NAME}' containers='${snapshotted[*]}'"
