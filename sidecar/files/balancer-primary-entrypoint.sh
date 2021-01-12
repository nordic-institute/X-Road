#!/bin/bash
source /root/_entrypoint_common.sh
#Configure master pod for balanacer
mkdir -p /run/sshd
chmod 0755 /run/sshd
cat /etc/.ssh/id_rsa.pub >> /home/xroad-slave/.ssh/authorized_keys
# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
