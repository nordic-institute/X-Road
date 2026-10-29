#!/bin/bash
set -e
INVOKED_FROM=$PWD
cd "$(dirname "${BASH_SOURCE[0]}")"
source "../../scripts/lib/base-script.sh"
source "./cluster/cluster-transport.sh"

RECREATE=false
SKIP_COMPILE=false
SKIP_TESTS=false
SKIP_BUILD=false
SKIP_INITIALIZE=false
SKIP_HOST_NETWORKING=false
BUST_CACHE=false
SNAPSHOT_EMPTY=false
PROFILE="single"
CUSTOM_INVENTORY=false
INVENTORY_PATH="config/ansible_hosts.txt"
CLUSTER_INVENTORY_PATH="config/ansible_hosts_cluster.txt"
CLUSTER_MIN_VM_MEMORY_GIB=20

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
    --profile=*)
      PROFILE="${1#*=}"
      if [[ "$PROFILE" != "single" && "$PROFILE" != "cluster" ]]; then
        log_error "Unknown profile: $PROFILE (expected single or cluster)"
        exit 1
      fi
      ;;
    --custom-inventory=*)
      CUSTOM_INVENTORY=true
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

  if [ "$PROFILE" = cluster ]; then
    if [ "$SNAPSHOT_EMPTY" = true ]; then
      log_error "--profile=cluster does not support --snapshot-empty-containers"
      exit 1
    fi
    if [ "$CUSTOM_INVENTORY" = false ]; then
      INVENTORY_PATH="$CLUSTER_INVENTORY_PATH"
    fi
  fi
  log_info "Execution plan:"
  log_kv "Profile" "$PROFILE" 2 5
  log_kv "Recreate containers" "$RECREATE" 2 5
  log_kv "Skip compile" "$SKIP_COMPILE" 2 5
  log_kv "Skip tests" "$SKIP_TESTS" 2 5
  log_kv "Skip build" "$SKIP_BUILD" 2 5
  log_kv "Skip Initialize with Hurl" "$SKIP_INITIALIZE" 2 5
  log_kv "Skip host networking apply" "$SKIP_HOST_NETWORKING" 2 5
  log_kv "Bust cached images" "$BUST_CACHE" 2 5
  log_kv "Snapshot empty containers" "$SNAPSHOT_EMPTY" 2 5
  log_kv "Using inventory" "$INVENTORY_PATH" 2 5
  if [ "$PROFILE" = cluster ]; then
    log_kv "Cluster inventory" "$CLUSTER_INVENTORY" 2 5
  fi
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
  echo " --profile=single|cluster  single (default): one Central Server node xrd-cs;"
  echo "                           cluster: two-node Central Server cluster instead of xrd-cs"
  echo "                           (not with --snapshot-empty-containers). The cluster itself is"
  echo "                           described by CLUSTER_INVENTORY (default cluster/inventory.yml);"
  echo "                           on Linux use cluster/inventory.linux.sample.yml with"
  echo "                           --custom-inventory=config/custom/linux-cluster.sample.hosts"
  echo "                           On macOS the lima VM needs at least ${CLUSTER_MIN_VM_MEMORY_GIB} GiB memory."
  echo " -h, --help                This help text."
  exit 1
}

function handleClusterVmMemory() {
  if [ "$PROFILE" != cluster ]; then
    return 0
  fi
  local bytes
  bytes=$(limactl list xroad-lxd --format '{{.Memory}}') || {
    log_error "Cannot read the memory of lima instance xroad-lxd"
    exit 1
  }
  if [[ ! "$bytes" =~ ^[0-9]+$ ]]; then
    log_error "Cannot read the memory of lima instance xroad-lxd: got '$bytes', expected a number of bytes"
    exit 1
  fi
  if [ "$bytes" -lt $((CLUSTER_MIN_VM_MEMORY_GIB * 1024 * 1024 * 1024)) ]; then
    log_error "--profile=cluster needs a lima VM with at least ${CLUSTER_MIN_VM_MEMORY_GIB} GiB memory; xroad-lxd has $((bytes / 1024 / 1024 / 1024)) GiB."
    log_error "Raise it, then run again:"
    log_error "  limactl stop xroad-lxd && limactl edit xroad-lxd --memory ${CLUSTER_MIN_VM_MEMORY_GIB} && limactl start xroad-lxd"
    log_error "or recreate the VM (deletes all containers): ./scripts/setup-mac.sh --recreate --memory=${CLUSTER_MIN_VM_MEMORY_GIB}"
    exit 1
  fi
}
function handleProfileGuard() {
  if [ "$RECREATE" = true ] || [ "$BUST_CACHE" = true ]; then
    return 0
  fi
  local existing
  existing=$(lxc list -f csv -c n)
  if [ "$PROFILE" = cluster ]; then
    if grep -qxF xrd-cs <<<"$existing"; then
      log_error "Container xrd-cs exists: the env was started without --profile=cluster. Run with --recreate to rebuild it as a cluster env."
      exit 1
    fi
  else
    local container containers
    containers=$(./cluster/list-cluster-containers.sh) || {
      log_error "Cannot list the cluster containers of $CLUSTER_INVENTORY"
      exit 1
    }
    while IFS= read -r container; do
      if grep -qxF "$container" <<<"$existing"; then
        log_error "Cluster container $container exists: the env was started with --profile=cluster. Run with --recreate to rebuild it as a single-node env."
        exit 1
      fi
    done <<<"$containers"
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
    local snap_names=()
    # Extract just the snapshot name from each URL path element.
    # while-read instead of mapfile: macOS ships bash 3.2, which lacks it.
    while IFS= read -r snap_name; do
      snap_names+=("$snap_name")
    done < <(echo "$snaps_json" | jq -r '.[]' 2>/dev/null | sed 's|.*/||')

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

  local become_flags=()
  if [[ "${ENABLE_NETDATA:-true}" =~ ^(false|0|no|NO|FALSE)$ ]]; then
    extra_vars+=("-e" "enable_netdata=false")
    log_info "Netdata host monitoring disabled for this run"
  else
    log_info "Netdata host monitoring will be installed (port 3999)"
    [ "$onMacOs" != "yes" ] && become_flags+=("--ask-become-pass")
  fi

  ANSIBLE_CONFIG="config/ansible.cfg" ansible-playbook -i "$INVENTORY_PATH" \
    ../../development/ansible/xroad_dev.yml \
    --forks 5 \
    --skip-tags compile,build-packages \
    "${extra_vars[@]}" \
    "${become_flags[@]}" \
    -vv
}

function handleCluster() {
  if [ "$PROFILE" = cluster ]; then
    ./cluster/start-cluster.sh -e cs_ds_tls_throwaway=false -e xroad_cs_dev_configuration=true
  fi
}

function applyHostNet() {
  if [ "$SKIP_HOST_NETWORKING" = true ]; then
    log_info "Skipping host networking apply (--skip-host-networking)"
    return 0
  fi
  if [ "$PROFILE" = cluster ]; then
    export SMOKE_HOST=xrd-cs1
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

    ./../../scripts/build-native-packages.sh -r resolute -r rpm-el9 $build_args
  fi
}

function handleInitialize() {
  if [ "$SKIP_INITIALIZE" = false ]; then
    lxc exec xrd-hurl -- bash -c "cd /opt/hurl && ./run-hurl.sh"
    ./wait-xroad-ready.sh
  fi
}

function main() {
  parse_arguments "$@"

  local onMacOs="no"
  if [[ $(uname) == "Darwin" ]]; then
    onMacOs="yes"
    ensure_lima
    handleClusterVmMemory
  fi
  handleProfileGuard
  handleRecreate
  handleBustCache
  handleBuild
  listCachedImages
  ensureCacheFilled
  listSnapshots
  handleAnsible
  handleCluster
  applyHostNet
  handleSnapshotEmpty
  handleInitialize
}

main "$@"
