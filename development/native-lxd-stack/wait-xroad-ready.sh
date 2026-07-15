#!/bin/bash
# Waits until the federation is ready for cross-server traffic: each security
# server's downloaded global configuration must list the counterpart server's
# address AND the e2e subsystems as approved security-server clients, and each
# PROXY must have loaded that globalconf generation — otherwise proxied calls
# fail with clientproxy.unknown_member. Server addresses and client
# registrations reach the shared parameters in separate globalconf generations,
# and the proxies consume globalconf through the remote source on its own
# refresh cycle (~60s), so a file on disk is not yet a view the proxy serves.
# The hurl bootstrap returns as soon as registrations are accepted on the
# central server; propagation is asynchronous end to end (globalconf
# regeneration + configuration-client download + proxy refresh, ~2-3 minutes).
# The proxy-view check uses the unauthenticated listClients metaservice, which
# reflects the proxy's loaded globalconf and produces no messagelog rows — the
# e2e messagelog scenarios assert exact archive counts, so a real proxied
# probe request would shift them.
set -euo pipefail

TIMEOUT="${1:-300}"
SHARED_PARAMS=/etc/xroad/globalconf/DEV/shared-params.xml
SUBSYSTEMS="TestService TestClient test-consumer"

# stdin: shared-params.xml (pretty-printed, one element per line, members before
# securityServers per the schema sequence). Exits 0 iff the given address is
# present and every named subsystem is referenced as a securityServer <client>
# via its subsystem id — the id-ref join is what registration approval adds.
check_params() {
  awk -v addr="$1" -v subs="$2" '
    /<subsystem id="/    { id = $0; sub(/.*<subsystem id="/, "", id); sub(/".*/, "", id) }
    /<subsystemCode>/    { code = $0; gsub(/.*<subsystemCode>|<\/.*/, "", code); codeof[id] = code }
    /<client>/           { ref = $0; gsub(/.*<client>|<\/.*/, "", ref); attached[codeof[ref]] = 1 }
    /<address>/          { a = $0; gsub(/.*<address>|<\/.*/, "", a); if (a == addr) addrok = 1 }
    END {
      if (!addrok) exit 1
      n = split(subs, want, " ")
      for (i = 1; i <= n; i++) if (!attached[want[i]]) exit 1
    }'
}

# The proxy's loaded view: listClients must contain every e2e subsystem.
proxy_view_ready() {
  local body
  body=$(curl -sf --max-time 10 "http://$1:8080/listClients") || return 1
  for sub in $SUBSYSTEMS; do
    grep -q ">$sub<" <<<"$body" || return 1
  done
}

ready() {
  lxc exec xrd-ss1 -- cat "$SHARED_PARAMS" 2>/dev/null | check_params "xrd-ss0.lxd" "$SUBSYSTEMS" \
    && lxc exec xrd-ss0 -- cat "$SHARED_PARAMS" 2>/dev/null | check_params "xrd-ss1.lxd" "$SUBSYSTEMS" \
    && proxy_view_ready "xrd-ss0.lxd" \
    && proxy_view_ready "xrd-ss1.lxd"
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
