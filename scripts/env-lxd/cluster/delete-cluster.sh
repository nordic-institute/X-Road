#!/bin/bash
# Deletes the cluster containers named in the cluster inventory (CLUSTER_INVENTORY, default inventory.yml).
# The shared package repository container and every other container stay.
set -euo pipefail
INVOKED_FROM=$PWD
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=cluster-transport.sh
source ./cluster-transport.sh

names=$(./list-cluster-containers.sh) || { echo "ERROR: cannot list the cluster containers; nothing deleted" >&2; exit 1; }
existing=$(lxc list -f csv -c n)

while IFS= read -r container; do
  [[ -n "$container" ]] || continue
  if grep -qxF "$container" <<<"$existing"; then
    echo "Deleting container: $container"
    lxc delete -f "$container"
  fi
done <<<"$names"

echo "Cleanup complete"
