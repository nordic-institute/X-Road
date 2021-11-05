#!/bin/bash
source /root/_entrypoint_common.sh
# Configure master pod for balancer

mkdir -p /run/sshd && chmod 0755 /run/sshd

# ensure that rsync can read /etc/xroad
shopt -s extglob
chown -R xroad:xroad /etc/xroad/!(xroad.properties)
chmod -R g+rX,o= /etc/xroad/!(xroad.properties)
shopt -u extglob

if [ -f /etc/.ssh/id_rsa.pub ]; then
  ln -f -s /etc/.ssh/id_rsa.pub /home/xroad-slave/.ssh/authorized_keys
fi

# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
