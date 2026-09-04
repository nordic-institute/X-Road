#!/bin/bash
#
# Enables operational monitoring and configures its connection over mutual
# TLS, matching the compose ss-opmonitor overlay's seed rows for the
# multi-container stack. The connection host/port are left at their built-in
# defaults: this container's proxy stays in NATIVE deployment mode (see
# _entrypoint_common.sh), whose default already resolves to the co-located
# op-monitor listener on localhost.
set -euo pipefail

set_property() {
  /usr/share/xroad/scripts/db_property.sh set "$1" "$2" -y
}

set_property xroad.proxy.addon.op-monitor.enabled true
set_property xroad.proxy.addon.op-monitor.connection.scheme https
set_property xroad.op-monitor.scheme https
set_property xroad.op-monitor.records-available-timestamp-offset-seconds 1
set_property xroad.op-monitor.tls.client-certificate-refresh-interval 60S
