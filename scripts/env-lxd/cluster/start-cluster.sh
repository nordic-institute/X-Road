#!/bin/bash
# Brings up the LXD Central Server cluster described by the cluster inventory (CLUSTER_INVENTORY, default
# inventory.yml). Safe to re-run: a run on an unchanged cluster changes nothing. It refuses to run while a
# database or etcd host exists and is not running.
#
# Extra arguments go to ansible-playbook. The local package check is skipped when the package source is
# remote: -e package_source=remote, or package_source: remote in the inventory. Only the plain
# -e package_source=... form is read; JSON and @file extra variables are not detected.
#
# A new container belongs to the cluster_containers group of the inventory, so this script creates it,
# list-cluster-containers.sh lists it and delete-cluster.sh removes it. A set-up step is idempotent.
set -euo pipefail
INVOKED_FROM=$PWD
cd "$(dirname "${BASH_SOURCE[0]}")"
source "../../lib/base-script.sh"
# shellcheck source=cluster-transport.sh
source ./cluster-transport.sh

PLAYBOOK="../../../development/ansible/xroad_cs_cluster.yml"
BUILD_DIR="../../../deployment/native-packages/build"
ANSIBLE_CFG="$(realpath ../config/ansible.cfg)"
EXTRA_ARGS=()

usage() {
  echo "Usage: $0 [ansible-playbook options]"
  echo "Options after the script name go to ansible-playbook, e.g. -e xroad_package_repo=\"deb ...\""
  echo "CLUSTER_INVENTORY selects the inventory (default inventory.yml; on a Linux LXD host inventory.linux.sample.yml)."
  echo "On Linux the LXD host tasks run without sudo, so the user must be in the lxd group;"
  echo "otherwise add: -e onMacOs=yes --ask-become-pass"
}

remote_packages() {
  local argument
  for argument in "$@"; do
    [[ "$argument" != *package_source=remote* ]] || return 0
    [[ "$argument" != *package_source=local* ]] || return 1
  done
  [[ "$(inventory_value "$(group_hosts cs_nodes | head -n 1)" package_source)" == remote ]]
}

check_packages() {
  if remote_packages "$@"; then
    log_info "Remote package source: no local packages needed"
    return 0
  fi
  if ! ls "$BUILD_DIR"/ubuntu*/xroad-centralserver_*.deb >/dev/null 2>&1; then
    log_error "No built Central Server packages under $BUILD_DIR; run scripts/build-native-packages.sh first"
    exit 1
  fi
}

print_addresses() {
  log_info "Cluster containers:"
  local names
  names=$(./list-cluster-containers.sh | paste -sd'|' -)
  lxc list -f csv -c n4 "^(${names})\$" | while IFS=, read -r name address; do
    log_kv "  $name" "${address%% *}" 2 5
  done
  log_info "Central Server admin UI: https://$(host_address "$(group_hosts cs_nodes | head -n 1)"):4000"
  log_info "PostgreSQL write address (Central Server nodes): $(inventory_value lb database_host)"
  log_info "PostgreSQL read-only address: $(inventory_value lb cluster_name):$(inventory_value lb db_read_port)"
  log_info "Cluster name (the LXD name of the load balancer host): $(inventory_value lb cluster_name)"
}

main() {
  case "${1:-}" in
    -h | --help)
      usage
      exit 0
      ;;
  esac
  EXTRA_ARGS=("$@")
  require_tools
  command -v ansible-playbook >/dev/null || die "ansible-playbook not found"
  ensure_lima
  check_packages ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"}
  local onMacOs="no"
  [[ "$(uname)" == "Darwin" ]] && onMacOs="yes"
  ANSIBLE_CONFIG="$ANSIBLE_CFG" ansible-playbook -i "$INVENTORY" "$PLAYBOOK" \
    --forks 5 \
    -e "onMacOs=$onMacOs" \
    ${EXTRA_ARGS[@]+"${EXTRA_ARGS[@]}"}
  print_addresses
}

main "$@"
