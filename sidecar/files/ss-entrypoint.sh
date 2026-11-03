#!/bin/bash
source /root/_entrypoint_common.sh
# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
