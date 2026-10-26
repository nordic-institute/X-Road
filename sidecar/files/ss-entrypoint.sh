#!/bin/bash

# Explicit switch, never auto-detected. On success the regular start follows
# in the same container.
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
