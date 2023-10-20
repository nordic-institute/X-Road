#!/bin/bash

log() { echo "$(date --utc -Iseconds) INFO [entrypoint] $*"; }
warn() { echo "$(date --utc -Iseconds) WARN [entrypoint] $*" >&2; }

exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
