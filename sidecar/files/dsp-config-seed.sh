#!/bin/bash
#############################################################################
#
# X-Road Security Server sidecar DSP configuration seed.
#
# The admin-service's own data space participant-context provisioning worker
# (DataspaceParticipantProvisioningWorker) reads a set of
# xroad.proxy-ui-api.dataspace.* rows - identity-hub-url has no built-in
# default, and the worker dereferences it on every scheduled tick regardless
# of whether data space credentials are ever requested, so leaving it unset
# does not just disable a feature, it makes every tick fail. This script
# seeds it, the same way every other deployment mode (compose, ansible, the
# k8s chart) seeds its equivalent row at provisioning time.
#
# The same worker also calls the identity-hub and ds-control-plane
# provisioning gRPC services to create/inspect participant contexts.
# xroad.dataspace.{control-plane,identity-hub}-provisioning.rpc.host default
# to the multi-container deployment's separate service names
# ("ds-control-plane"/"ds-identity-hub"), which do not resolve to anything
# inside a single sidecar container - both processes are always co-located
# here, so this seeds both to 127.0.0.1 unconditionally (not
# operator-configurable: there is no sidecar shape where they would run
# anywhere else).
#
# Contract:
#   in  - XROAD_DATASPACE_IDENTITY_HUB_URL (optional, defaults to
#           https://<hostname>:7183 - override with the externally-resolvable
#           address other participants reach this container's identity-hub
#           through, e.g. a shared network alias)
#         /etc/xroad/db.properties (serverconf connection info, written by
#         xroad-proxy's setup_serverconf_db.sh before this script runs)
#   out - the rows above in serverconf's DB configuration properties, each
#         inserted only if absent; an existing row (operator-set, or seeded
#         on an earlier boot) is left untouched. The row writes and the
#         serverconf connection details come from the packaged
#         /usr/share/xroad/scripts/db_property.sh, whose --if-absent mode
#         provides the never-overwrite semantics.
#
#############################################################################
set -euo pipefail

log() { echo "$(date --utc -Iseconds) INFO [dsp-config-seed] $*"; }

seed_property_if_absent() {
  local key="$1" value="$2"
  log "Seeding ${key} unless the row already exists"
  /usr/share/xroad/scripts/db_property.sh set --if-absent -- "$key" "$value"
}

seed_property_if_absent "xroad.proxy-ui-api.dataspace.identity-hub-url" \
  "${XROAD_DATASPACE_IDENTITY_HUB_URL:-https://${HOSTNAME:-localhost}:7183}"

seed_property_if_absent "xroad.common-rpc.channel.control-plane.host" "127.0.0.1"

seed_property_if_absent "xroad.dataspace.identity-hub-provisioning.rpc.host" "127.0.0.1"
