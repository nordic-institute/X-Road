#!/bin/bash
# Waits until the federation is ready for cross-server traffic: each security
# server's downloaded global configuration must list the counterpart server's
# address, otherwise proxied calls fail with clientproxy.unknown_member. The
# hurl bootstrap returns as soon as registrations are accepted on the central
# server; propagation to the security servers is asynchronous (globalconf
# regeneration + configuration-client download cycle, up to ~2 minutes).
# Deliberately reads files instead of sending a probe request through the
# proxies: the e2e messagelog scenarios assert exact archive counts, and any
# probe traffic would shift them.
set -euo pipefail

TIMEOUT="${1:-300}"
SHARED_PARAMS=/etc/xroad/globalconf/DEV/shared-params.xml

ready() {
  lxc exec xrd-ss1 -- grep -q "<address>xrd-ss0.lxd</address>" "$SHARED_PARAMS" 2>/dev/null \
    && lxc exec xrd-ss0 -- grep -q "<address>xrd-ss1.lxd</address>" "$SHARED_PARAMS" 2>/dev/null
}

start=$(date +%s)
until ready; do
  if (($(date +%s) - start >= TIMEOUT)); then
    echo "wait-xroad-ready: globalconf propagation not complete after ${TIMEOUT}s" >&2
    exit 1
  fi
  sleep 5
done
# Settle margin for the proxies to pick up the changed globalconf and OCSP state.
sleep 15
echo "wait-xroad-ready: globalconf propagated in $(($(date +%s) - start))s"
