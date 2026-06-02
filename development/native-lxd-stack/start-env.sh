#!/bin/bash
set -e
source "${BASH_SOURCE%/*}/../../.scripts/base-script.sh"

RECREATE=false
SKIP_COMPILE=false
SKIP_TESTS=false
SKIP_BUILD=false
SKIP_INITIALIZE=false
SKIP_HOST_NETWORKING=false
BUST_CACHE=false
SNAPSHOT_EMPTY=false
INVENTORY_PATH="config/ansible_hosts.txt"

CACHEABLE_ROLES=(xroad-is xroad-ca xroad-repo)
UBUNTU_RELEASEVER="${UBUNTU_RELEASEVER:-resolute}"

function parse_arguments() {
  while [[ "$#" -gt 0 ]]; do
    case $1 in
    --recreate) RECREATE=true ;;
    --skip-compile) SKIP_COMPILE=true ;;
    --skip-tests) SKIP_TESTS=true ;;
    --skip-build) SKIP_BUILD=true ;;
    --skip-init) SKIP_INITIALIZE=true ;;
    --skip-host-networking) SKIP_HOST_NETWORKING=true ;;
    --bust-cache) BUST_CACHE=true ;;
    --snapshot-empty-containers) SNAPSHOT_EMPTY=true ;;
    --custom-inventory=*)
      INVENTORY_PATH="${1#*=}"
      if [ ! -f "$INVENTORY_PATH" ]; then
        log_error "Inventory file not found: $INVENTORY_PATH"
        exit 1
      fi
      ;;
    -h | --help) usage ;;
    *)
      echo "Unknown parameter: $1"
      exit 1
      ;;
    esac
    shift
  done

  # Validate flags
  if [ "$SKIP_BUILD" = true ] && [ "$SKIP_COMPILE" = true ]; then
    log_error "--skip-build already includes compile skipping. Don't use both flags."
    exit 1
  fi

  log_info "Execution plan:"
  log_kv "Recreate containers" "$RECREATE" 2 5
  log_kv "Skip compile" "$SKIP_COMPILE" 2 5
  log_kv "Skip tests" "$SKIP_TESTS" 2 5
  log_kv "Skip build" "$SKIP_BUILD" 2 5
  log_kv "Skip Initialize with Hurl" "$SKIP_INITIALIZE" 2 5
  log_kv "Skip host networking apply" "$SKIP_HOST_NETWORKING" 2 5
  log_kv "Bust cached images" "$BUST_CACHE" 2 5
  log_kv "Snapshot empty containers" "$SNAPSHOT_EMPTY" 2 5
  log_kv "Using inventory" "$INVENTORY_PATH" 2 5
}

usage() {
  echo "Usage: $0 [options]"
  echo "Options:"
  echo " --skip-compile              Skip compilation phase"
  echo " --skip-tests               Skip test execution"
  echo " --skip-build               Skip both compilation and package building"
  echo " --recreate                Recreate containers"
  echo " --initialize              Initialize with Hurl"
  echo " --skip-host-networking    Skip host network apply (no sudo password prompt)"
  echo " --bust-cache              Delete cached LXD images and refill"
  echo " --snapshot-empty-containers  Take 'empty' snapshot of all containers before hurl init"
  echo " --custom-inventory=PATH   Use custom inventory file instead of default"
  echo " -h, --help                This help text."
  exit 1
}

function handlePrepare() {
  if limactl list | grep -q '^xroad-lxd'; then
    # Check current status
    current_status=$(limactl list | grep '^xroad-lxd' | awk '{print $2}')

    if [ "$current_status" = "Running" ]; then
      log_info "Lima instance xroad-lxd is already running"
    elif [ "$current_status" = "Stopped" ]; then
      log_info "Starting lima instance xroad-lxd"
      limactl start xroad-lxd

      # Verify that the instance is running
      if limactl list | grep '^xroad-lxd' | awk '{print $2}' | grep -q 'Running'; then
        log_info "Lima instance xroad-lxd started successfully"
      else
        log_error "Failed to start lima instance xroad-lxd"
        exit 1
      fi
    else
      log_info "Lima instance xroad-lxd has status: $current_status - waiting for it to be ready"
      # Wait for the instance to reach a stable state
      sleep 5
      handlePrepare # Recursive call to check again
    fi
  else
    log_error "Lima instance xroad-lxd not found. Please create it first."
    exit 1
  fi
}

function handleRecreate() {
  if [ "$RECREATE" = true ]; then
    ./scripts/delete-env.sh
  fi
}

function handleBustCache() {
  if [ "$BUST_CACHE" = true ]; then
    ./scripts/delete-env.sh
    for role in "${CACHEABLE_ROLES[@]}"; do
      local alias="${role}-cached"
      if lxc image info "$alias" >/dev/null 2>&1; then
        log_info "Deleting cached image $alias"
        lxc image delete "$alias" || true
      fi
    done
  fi
}

function listCachedImages() {
  log_info "Cached LXD images:"
  local upstream_fp
  upstream_fp=$(lxc image info "ubuntu:${UBUNTU_RELEASEVER}" --format=json 2>/dev/null \
    | jq -r '.fingerprint // ""')
  for role in "${CACHEABLE_ROLES[@]}"; do
    local alias="${role}-cached"
    local info
    info=$(lxc image info "$alias" --format=json 2>/dev/null || echo "")
    if [ -z "$info" ]; then
      log_kv "  $alias" "(missing — will fill on launch)" 2 3
      continue
    fi
    local fp uploaded stamped match
    fp=$(echo "$info" | jq -r '.fingerprint' | cut -c1-12)
    uploaded=$(echo "$info" | jq -r '.uploaded_at // "unknown"')
    stamped=$(echo "$info" | jq -r '.properties.ubuntu_base // ""')
    if [ -z "$upstream_fp" ]; then
      match="upstream unreachable"
    elif [ -z "$stamped" ]; then
      match="ubuntu_base not stamped"
    elif [ "$stamped" = "$upstream_fp" ]; then
      match="ubuntu fresh"
    else
      match="ubuntu STALE — will rebuild"
    fi
    log_kv "  $alias" "$fp ($uploaded) — $match" 2 5
  done
}

SNAPSHOT_CONTAINERS=(xrd-ss0 xrd-ss1 xrd-cs)

function listSnapshots() {
  log_info "LXD container snapshots:"
  for container in "${SNAPSHOT_CONTAINERS[@]}"; do
    if ! lxc info "$container" >/dev/null 2>&1; then
      continue
    fi

    # Determine the storage pool for this container
    local pool
    pool=$(lxc query "/1.0/instances/${container}" 2>/dev/null \
      | jq -r '.expanded_devices.root.pool // ""')

    # Enumerate snapshots via API; filter to empty/custom only
    local snaps_json
    snaps_json=$(lxc query "/1.0/instances/${container}/snapshots" 2>/dev/null || echo "[]")

    local found=false
    local snap_names
    # Extract just the snapshot name from each URL path element
    mapfile -t snap_names < <(echo "$snaps_json" | jq -r '.[]' 2>/dev/null \
      | sed 's|.*/||')

    for snap in "${snap_names[@]}"; do
      [[ "$snap" == "empty" || "$snap" == "custom" ]] || continue
      found=true

      # Get creation timestamp from snapshot detail
      local snap_detail created_at size_str
      snap_detail=$(lxc query "/1.0/instances/${container}/snapshots/${snap}" 2>/dev/null || echo "{}")
      created_at=$(echo "$snap_detail" | jq -r '.created_at // "unknown"' 2>/dev/null || echo "unknown")

      # Attempt size lookup via storage pool state endpoint
      size_str="—"
      if [ -n "$pool" ]; then
        local size_bytes
        size_bytes=$(lxc query \
          "/1.0/storage-pools/${pool}/volumes/container/${container}/snapshots/${snap}/state" \
          2>/dev/null | jq -r '.usage // empty' 2>/dev/null || true)
        if [ -n "$size_bytes" ] && [ "$size_bytes" != "null" ]; then
          if command -v numfmt >/dev/null 2>&1; then
            size_str=$(numfmt --to=iec "$size_bytes" 2>/dev/null || echo "${size_bytes}B")
          else
            size_str="${size_bytes}B"
          fi
        fi
      fi

      log_kv "  ${container}/${snap}" "${created_at} — ${size_str}" 2 5
    done

    if [ "$found" = false ]; then
      log_kv "  $container" "(no snapshots)" 2 3
    fi
  done
}

function handleSnapshotEmpty() {
  if [ "$SNAPSHOT_EMPTY" = true ]; then
    ./scripts/snapshot-containers.sh --name=empty
  fi
}

function ensureCacheFilled() {
  local needs_fill=()
  local upstream_fp
  upstream_fp=$(lxc image info "ubuntu:${UBUNTU_RELEASEVER}" --format=json 2>/dev/null \
    | jq -r '.fingerprint // ""')
  for role in "${CACHEABLE_ROLES[@]}"; do
    local alias="${role}-cached"
    if ! lxc image info "$alias" >/dev/null 2>&1; then
      needs_fill+=("$role")
      continue
    fi
    if [ -n "$upstream_fp" ]; then
      local stamped
      stamped=$(lxc image info "$alias" --format=json 2>/dev/null \
        | jq -r '.properties.ubuntu_base // ""')
      if [ "$stamped" != "$upstream_fp" ]; then
        log_info "Cached image $alias is stale (Ubuntu base drifted) — rebuilding"
        lxc image delete "$alias" || true
        needs_fill+=("$role")
      fi
    fi
  done

  if [ ${#needs_fill[@]} -eq 0 ]; then
    log_info "All cached images present and fresh"
    return
  fi

  local targets
  targets=$(IFS=,; echo "${needs_fill[*]}")
  log_info "Filling cache for: ${needs_fill[*]}"

  local extra_vars=("-e" "onMacOs=$onMacOs" "-e" "cache_targets=$targets")
  if [[ -n "$XROAD_MIRROR_UBUNTU_URL" ]] && [[ -n "$XROAD_MIRROR_USERNAME" ]] && [[ -n "$XROAD_MIRROR_TOKEN" ]]; then
    extra_vars+=("-e" "package_mirror_url=$XROAD_MIRROR_UBUNTU_URL")
    extra_vars+=("-e" "package_mirror_user=$XROAD_MIRROR_USERNAME")
    extra_vars+=("-e" "package_mirror_token=$XROAD_MIRROR_TOKEN")
  fi

  ANSIBLE_CONFIG="config/ansible.cfg" ansible-playbook -i "$INVENTORY_PATH" \
    ../../development/ansible/xroad_cache_images.yml \
    --forks 2 \
    "${extra_vars[@]}" \
    -vv
}

function handleAnsible() {
  # Use XROAD_MIRROR_* env vars directly (no resolve script needed)
  local extra_vars=("-e" "onMacOs=$onMacOs")
  if [[ -n "$XROAD_MIRROR_UBUNTU_URL" ]] && [[ -n "$XROAD_MIRROR_USERNAME" ]] && [[ -n "$XROAD_MIRROR_TOKEN" ]]; then
    extra_vars+=("-e" "package_mirror_url=$XROAD_MIRROR_UBUNTU_URL")
    extra_vars+=("-e" "package_mirror_user=$XROAD_MIRROR_USERNAME")
    extra_vars+=("-e" "package_mirror_token=$XROAD_MIRROR_TOKEN")
  fi

  # Netdata host-level monitoring on the LXD host: ON by default.
  # Set ENABLE_NETDATA=false (or 0/no) to skip the netdata role for a run.
  if [[ "${ENABLE_NETDATA:-true}" =~ ^(false|0|no|NO|FALSE)$ ]]; then
    extra_vars+=("-e" "enable_netdata=false")
    log_info "Netdata host monitoring disabled for this run"
  else
    log_info "Netdata host monitoring will be installed (port 3999)"
  fi

  ANSIBLE_CONFIG="config/ansible.cfg" ansible-playbook -i "$INVENTORY_PATH" \
    ../../development/ansible/xroad_dev.yml \
    --forks 5 \
    --skip-tags compile,build-packages \
    "${extra_vars[@]}" \
    -vv
}

function applyHostNet() {
  if [ "$SKIP_HOST_NETWORKING" = true ]; then
    log_info "Skipping host networking apply (--skip-host-networking)"
    return 0
  fi
  case "$(uname)" in
    Darwin) ./scripts/setup-mac-net.sh apply ;;
    Linux)  ./scripts/setup-linux-net.sh apply ;;
    *)      return 0 ;;
  esac
}

function handleBuild() {
  local build_args=""
  if [ "$SKIP_BUILD" = false ]; then

    if [ "$SKIP_COMPILE" = true ]; then
      build_args+="--package-only "
    fi
    if [ "$SKIP_TESTS" = true ]; then
      build_args+="--skip-tests "
    fi

    ./../../src/build_packages.sh -r resolute -r rpm-el9 $build_args
  fi
}

function handleInitialize() {
  if [ "$SKIP_INITIALIZE" = false ]; then
    lxc exec xrd-hurl -- bash -c "cd /opt/hurl && ./run-hurl.sh"
  fi
}

function main() {
  parse_arguments "$@"

  local onMacOs="no"
  if [[ $(uname) == "Darwin" ]]; then
    onMacOs="yes"
    # lima is only for MacOS
    handlePrepare
  fi
  handleRecreate
  handleBustCache
  handleBuild
  listCachedImages
  ensureCacheFilled
  listSnapshots
  handleAnsible
  applyHostNet
  handleSnapshotEmpty
  handleInitialize
}

main "$@"
