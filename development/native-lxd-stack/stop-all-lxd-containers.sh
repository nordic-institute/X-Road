#!/bin/bash
set -e
source "${BASH_SOURCE%/*}/../../.scripts/base-script.sh"

echo "Listing all LXD containers..."
lxc list
echo "Stopping all running LXD containers..."
lxc list -c n,s --format csv | awk -F, '$2=="RUNNING"{print $1}' | xargs -r -n1 lxc stop