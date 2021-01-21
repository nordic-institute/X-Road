#!/bin/bash
GROUPNAMES="xroad-securityserver-observer"

# Configure admin user with user-supplied username and password
if ! getent passwd "$XROAD_ADMIN_USER" &>/dev/null; then
    echo "Creating admin user with user-supplied credentials"
    useradd -m "${XROAD_ADMIN_USER}" -s /usr/sbin/nologin
    echo "${XROAD_ADMIN_USER}:${XROAD_ADMIN_PASSWORD}" | chpasswd
    echo "xroad-proxy xroad-common/username string ${XROAD_ADMIN_USER}" | debconf-set-selections

    echo "Configuring groups"
    usergroups=" $(id -Gn "${XROAD_ADMIN_USER}") "

    for groupname in ${GROUPNAMES}; do
        if [[ $usergroups != *" $groupname "* ]]; then
            echo "$groupname"
            usermod -a -G "$groupname" "${XROAD_ADMIN_USER}" || true
        fi
    done
fi

chown -R xroad:xroad /etc/xroad

#Try rsync until success
RC=1
while [[ $RC -ne 0 ]]
do
 sleep 5 || exit 1
 su -c "rsync -q -e 'ssh -i /etc/.ssh/id_rsa -o StrictHostKeyChecking=no -o ConnectTimeout=5 ' -avz --timeout=10 --delete-delay  --exclude '/conf.d/node.ini' --exclude '*.tmp'  --delay-updates --log-file=/var/log/xroad/slave-sync.log xroad-slave@${XROAD_PRIMARY_DNS}:/etc/xroad/ /etc/xroad/" xroad
 RC=$?
done

#Create cron job for rsync every minute
echo "* * * * * xroad rsync -e 'ssh -i /etc/.ssh/id_rsa -o StrictHostKeyChecking=no -o ConnectTimeout=5' -avz --timeout=10 --delete-delay  --exclude '/conf.d/node.ini' --exclude '*.tmp'  --delay-updates --log-file=/var/log/xroad/slave-sync.log xroad-slave@$XROAD_PRIMARY_DNS:/etc/xroad/ /etc/xroad/ 2>&1" > /etc/cron.d/xroad-state-sync
chown root:root /etc/cron.d/xroad-state-sync && chmod 644 /etc/cron.d/xroad-state-sync

# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
