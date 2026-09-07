#!/bin/bash
#
# Seeds the e2e configuration rows for the sidecar ss0, mirroring what the
# multi-container ss0 gets from its compose seed sources; each section below
# cites the file it must stay in sync with.
set -euo pipefail

set_property() {
  /usr/share/xroad/scripts/db_property.sh set "$1" "$2" -y
}

# Base e2e seed rows (compose.e2e.yaml), for the properties that are still
# meaningful on a single-container deployment. The dataspace
# participant/identity-hub rows from that seed are not repeated here: this
# image's own dsp-config-seed.sh already seeds their sidecar equivalents from
# the container's environment.
set_property xroad.proxy.message-log.timestamper.timestamp-immediately true
set_property xroad.common-global-conf.refresh-rate 10S
set_property xroad.configuration-client.update-interval 10
set_property xroad.dsp.catalog.cache.ttl-seconds 5
set_property xroad.proxy.dsp.serverproxy-endpoint "https://${XROAD_DSP_PARTICIPANT_CONTEXT_ID:-xrd-ss0}:5500"

# Batch signing (compose.ss-batch-signature-enabled.e2e.yaml).
set_property xroad.proxy.batch-signing-enabled true

# Operational monitoring over mutual TLS (compose.ss-opmonitor.e2e.yaml).
# The connection host/port are left at their built-in defaults: this
# container's proxy stays in NATIVE deployment mode (see
# _entrypoint_common.sh), whose default already resolves to the co-located
# op-monitor listener on localhost.
set_property xroad.proxy.addon.op-monitor.enabled true
set_property xroad.proxy.addon.op-monitor.connection.scheme https
set_property xroad.op-monitor.scheme https
set_property xroad.op-monitor.records-available-timestamp-offset-seconds 1
set_property xroad.op-monitor.tls.client-certificate-refresh-interval 60S

# Message-log archiver (compose.ss-msglog.e2e.yaml): the harness triggers
# archiving and cleanup explicitly, so the built-in scheduled jobs are
# disabled, and retained records are kept for 0 days so a just-archived
# record is immediately eligible for cleanup instead of waiting out the
# default 30-day window.
set_property xroad.auxiliary-service.message-log.archive-cron off
set_property xroad.auxiliary-service.message-log.clean-cron disabled
set_property xroad.message-log-archiver.clean-keep-records-for 0
