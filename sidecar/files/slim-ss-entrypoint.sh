#!/bin/bash
source /root/_entrypoint_common.sh
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
