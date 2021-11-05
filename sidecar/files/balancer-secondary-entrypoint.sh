#!/bin/bash

if [ -n "$XROAD_ADMIN_USER" ]; then
  # Configure admin user with user-supplied username and password
  if ! getent passwd "$XROAD_ADMIN_USER" &>/dev/null; then
    echo "Creating admin user with user-supplied credentials"

    if ! getent group "xroad-securityserver-observer" &>/dev/null; then
      groupadd xroad-securityserver-observer
    fi
    useradd -m "${XROAD_ADMIN_USER}" -s /usr/sbin/nologin -G xroad-securityserver-observer
    echo "${XROAD_ADMIN_USER}:${XROAD_ADMIN_PASSWORD}" | chpasswd
    echo "xroad-proxy xroad-common/username string ${XROAD_ADMIN_USER}" | debconf-set-selections
  fi
fi
XROAD_ADMIN_PASSWORD=

chown -R xroad:xroad /etc/xroad

#Try rsync until success
RC=1
while [[ $RC -ne 0 ]]
do
 sleep 5 || exit 1
 su -c "rsync -q -e 'ssh -i /etc/.ssh/id_rsa -o StrictHostKeyChecking=no -o ConnectTimeout=5 ' -avz --timeout=10 --delete-delay --exclude /xroad.properties --exclude '/conf.d/node.ini' --exclude '*.tmp'  --delay-updates --log-file=/var/log/xroad/slave-sync.log xroad-slave@${XROAD_PRIMARY_DNS}:/etc/xroad/ /etc/xroad/" xroad
 RC=$?
done

#Create cron job for rsync every minute
echo "* * * * * xroad rsync -e 'ssh -i /etc/.ssh/id_rsa -o StrictHostKeyChecking=no -o ConnectTimeout=5' -avz --timeout=10 --delete-delay --exclude /xroad.properties --exclude '/conf.d/node.ini' --exclude '*.tmp'  --delay-updates --log-file=/var/log/xroad/slave-sync.log xroad-slave@$XROAD_PRIMARY_DNS:/etc/xroad/ /etc/xroad/ 2>&1" > /etc/cron.d/xroad-state-sync
chown root:root /etc/cron.d/xroad-state-sync && chmod 644 /etc/cron.d/xroad-state-sync

# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
