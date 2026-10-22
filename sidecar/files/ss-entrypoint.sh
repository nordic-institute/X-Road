#!/bin/bash

# The migration switch: an explicit environment variable, never auto-detected,
# so a regular start never migrates by accident. Taken before any regular
# provisioning so migration mode never starts supervisord or any X-Road
# service while it is running, for example:
#   docker run -e XROAD_MIGRATION_MODE=true ... <image>
# A non-zero result ends the container here; a successful migration (or one
# already marked complete) falls through to the regular start below, in the
# same container.
if [ "${XROAD_MIGRATION_MODE:-}" = "true" ]; then
  /usr/share/xroad/scripts/sidecar/migration/orchestrator.sh
  migration_status=$?
  if [ "$migration_status" -ne 0 ]; then
    exit "$migration_status"
  fi
fi

source /root/_entrypoint_common.sh
# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
