#!/bin/bash
source /root/_entrypoint_common.sh

mkdir -p /run/sshd && chmod 0755 /run/sshd
log "$(ssh-keygen -A)"

# ensure that rsync can read /etc/xroad
shopt -s extglob
chown -R xroad:xroad /etc/xroad/!(xroad.properties|gpghome)
chmod -R g+rX,o= /etc/xroad/!(xroad.properties|gpghome)
shopt -u extglob

if [ -f /etc/.ssh/id_rsa.pub ]; then
  ln -f -s /etc/.ssh/id_rsa.pub /home/xroad-slave/.ssh/authorized_keys
fi

# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
