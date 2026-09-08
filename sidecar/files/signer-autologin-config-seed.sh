#!/bin/bash
#############################################################################
#
# X-Road Security Server sidecar signer autologin enable-flag seed.
#
# xroad.signer.autologin.enabled (XRDADR-34) is resolved by the signer's
# XRoadConfig DSL, which only consults the serverconf configuration_properties
# table and packaged defaults - it is not publishedToFramework() and has no
# environment-variable or JVM-system-property binding, unlike the token PINs
# (xroad.signer.autologin.tokens.*.pin), which are a plain smallrye
# @ConfigMapping and so read XROAD_SIGNER_AUTOLOGIN_TOKENS__<id>__PIN directly
# from the process environment. This script is the enable flag's equivalent
# of the DSP participant-context-id seed: the sidecar's bridge from a
# docker-run environment variable to the DB-config-override row the signer
# actually reads. No row is written unless the operator opts in, so an
# unconfigured container keeps the packaged default (disabled).
#
# Contract:
#   in  - XROAD_SIGNER_AUTOLOGIN_ENABLED (operator opt-in; unset/empty = skip
#         entirely, no row written, packaged default "false" stays in effect)
#   out - a xroad.signer.autologin.enabled row in serverconf's
#         configuration_properties table, inserted only if the row is
#         absent; an existing row (operator-set, or seeded on an earlier
#         boot) is left untouched. The row write and the serverconf
#         connection details both come from the packaged
#         /usr/share/xroad/scripts/db_property.sh, whose --if-absent mode
#         provides the never-overwrite semantics.
#
#############################################################################
set -euo pipefail

log() { echo "$(date --utc -Iseconds) INFO [signer-autologin-config-seed] $*"; }

if [ -z "${XROAD_SIGNER_AUTOLOGIN_ENABLED:-}" ]; then
  exit 0
fi

KEY="xroad.signer.autologin.enabled"

case "$(echo "$XROAD_SIGNER_AUTOLOGIN_ENABLED" | tr '[:upper:]' '[:lower:]')" in
true) value=true ;;
false) value=false ;;
*)
  echo "XROAD_SIGNER_AUTOLOGIN_ENABLED must be 'true' or 'false', got '${XROAD_SIGNER_AUTOLOGIN_ENABLED}' - skipping seed" >&2
  exit 0
  ;;
esac

log "Seeding ${KEY} = ${value} unless the row already exists"
exec /usr/share/xroad/scripts/db_property.sh set "$KEY" "$value" --if-absent
