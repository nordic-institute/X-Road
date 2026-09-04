#!/bin/bash
#
# Matches the compose ss-msglog overlay's config-seed-msglog rows for the
# multi-container stack: the harness triggers archiving and cleanup
# explicitly, so the built-in scheduled jobs are disabled, and retained
# records are kept for 0 days so a just-archived record is immediately
# eligible for cleanup instead of waiting out the default 30-day window.
set -euo pipefail

set_property() {
  /usr/share/xroad/scripts/db_property.sh set "$1" "$2" -y
}

set_property xroad.auxiliary-service.message-log.archive-cron off
set_property xroad.auxiliary-service.message-log.clean-cron disabled
set_property xroad.message-log-archiver.clean-keep-records-for 0
