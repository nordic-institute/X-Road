#!/bin/bash
# Prints the names of the cluster containers in the cluster inventory (CLUSTER_INVENTORY, default
# inventory.yml), one per line, without the package repository container that the dev env shares.
# Exits non-zero, printing nothing, when the inventory cannot be read or lists no container.
set -euo pipefail
INVOKED_FROM=$PWD
cd "$(dirname "${BASH_SOURCE[0]}")"
# shellcheck source=cluster-transport.sh
source ./cluster-transport.sh

containers=$(group_hosts cluster_containers) || die "cannot list the cluster containers of $INVENTORY"
[[ -n "$containers" ]] || die "$INVENTORY lists no cluster containers"
repo_hosts=$(group_hosts repo_servers) || die "cannot list the repo_servers of $INVENTORY"

while IFS= read -r container; do
  grep -qxF -- "$container" <<<"$repo_hosts" || echo "$container"
done <<<"$containers"
