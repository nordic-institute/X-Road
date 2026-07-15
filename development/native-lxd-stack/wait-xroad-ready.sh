#!/bin/bash
# Waits until the federation is ready for cross-server traffic: each security
# server's downloaded global configuration must list the counterpart server's
# address AND the e2e subsystems as approved security-server clients, otherwise
# proxied calls fail with clientproxy.unknown_member. Server addresses and
# client registrations reach the shared parameters in separate globalconf
# generations — an address-only check passes before provider resolution works.
# The hurl bootstrap returns as soon as registrations are accepted on the
# central server; propagation to the security servers is asynchronous
# (globalconf regeneration + configuration-client download cycle, ~2 minutes).
# Deliberately reads files instead of sending a probe request through the
# proxies: the e2e messagelog scenarios assert exact archive counts, and any
# probe traffic would shift them.
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

ready() {
  lxc exec xrd-ss1 -- cat "$SHARED_PARAMS" 2>/dev/null | check_params "xrd-ss0.lxd" "$SUBSYSTEMS" \
    && lxc exec xrd-ss0 -- cat "$SHARED_PARAMS" 2>/dev/null | check_params "xrd-ss1.lxd" "$SUBSYSTEMS"
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
