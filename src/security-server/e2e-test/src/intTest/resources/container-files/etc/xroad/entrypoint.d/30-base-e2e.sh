#!/bin/bash
#
# Carries over the base e2e seed rows the multi-container ss0 gets from
# compose.e2e.yaml, for the properties that are still meaningful on a
# single-container deployment. The dataspace participant/identity-hub rows
# from that seed are not repeated here: this image's own dsp-config-seed.sh
# already seeds their sidecar equivalents from the container's environment.
set -euo pipefail

set_property() {
  /usr/share/xroad/scripts/db_property.sh set "$1" "$2" -y
}

set_property xroad.proxy.message-log.timestamper.timestamp-immediately true
set_property xroad.common-global-conf.refresh-rate 10S
set_property xroad.configuration-client.update-interval 10
set_property xroad.dsp.catalog.cache.ttl-seconds 5
set_property xroad.proxy.dsp.serverproxy-endpoint \
  "https://${XROAD_DSP_PARTICIPANT_CONTEXT_ID:-xrd-ss0}:5500"
