#!/bin/bash
# Delete `empty` and/or `custom` LXD snapshots across the default xrd-* container set.
#
# Usage:
#   cleanup-snapshots.sh --name=empty|custom [--containers="xrd-a xrd-b"]
#   cleanup-snapshots.sh --all               [--containers="xrd-a xrd-b"]
set -e

source "${BASH_SOURCE%/*}/../../../scripts/lib/base-script.sh"

DEFAULT_CONTAINERS="xrd-ss0 xrd-ss1 xrd-cs"
CONTAINERS=""
SNAPSHOT_NAME=""
ALL=0

for arg in "$@"; do
  case "$arg" in
    --name=*)
      SNAPSHOT_NAME="${arg#--name=}"
      ;;
    --all)
      ALL=1
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

# Validate mutually exclusive selectors
if (( ALL == 1 )) && [[ -n "$SNAPSHOT_NAME" ]]; then
  log_error "--name and --all are mutually exclusive; supply exactly one"
  exit 1
fi

if (( ALL == 0 )) && [[ -z "$SNAPSHOT_NAME" ]]; then
  log_error "No selector supplied; use --name=empty|custom or --all"
  exit 1
fi

# Validate --name value
if [[ -n "$SNAPSHOT_NAME" ]] && [[ "$SNAPSHOT_NAME" != "empty" && "$SNAPSHOT_NAME" != "custom" ]]; then
  log_error "Invalid --name value: '$SNAPSHOT_NAME' (allowed: empty, custom)"
  exit 1
fi

# Build snapshot list
if (( ALL == 1 )); then
  SNAPSHOTS=("empty" "custom")
else
  SNAPSHOTS=("$SNAPSHOT_NAME")
fi

# Resolve container set
if [[ -z "$CONTAINERS" ]]; then
  CONTAINERS="$DEFAULT_CONTAINERS"
fi
read -ra CONTAINER_LIST <<< "$CONTAINERS"

# Existence check helpers
function container_exists() {
  lxc info "$1" &>/dev/null
}

function snapshot_exists() {
  local container="$1" snapshot="$2"
  lxc query "/1.0/instances/${container}/snapshots/${snapshot}" &>/dev/null
}

# Main loop — fail fast on first missing container or snapshot
deleted=()

for container in "${CONTAINER_LIST[@]}"; do
  if ! container_exists "$container"; then
    log_error "Container not found: $container"
    exit 1
  fi

  for snapshot in "${SNAPSHOTS[@]}"; do
    if ! snapshot_exists "$container" "$snapshot"; then
      log_error "Snapshot '$snapshot' not found on container '$container'"
      exit 1
    fi
  done
done

# All existence checks passed — now delete
for container in "${CONTAINER_LIST[@]}"; do
  for snapshot in "${SNAPSHOTS[@]}"; do
    log_info "Deleting ${container}/${snapshot}"
    lxc delete "${container}/${snapshot}"
    deleted+=("${container}/${snapshot}")
  done
done

log_info "Deleted ${#deleted[@]} snapshot(s): ${deleted[*]}"
