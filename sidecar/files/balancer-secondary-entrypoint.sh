#!/bin/bash
log() { echo "$(date --utc -Iseconds) INFO [entrypoint] $*"; }
warn() { echo "$(date --utc -Iseconds) WARN [entrypoint] $*" >&2; }

if [ -n "$XROAD_ADMIN_USER" ]; then
  # Configure admin user with user-supplied username and password
  if ! getent passwd "$XROAD_ADMIN_USER" &>/dev/null; then
    log "Creating admin user with user-supplied credentials"

    if ! getent group "xroad-securityserver-observer" &>/dev/null; then
      groupadd xroad-securityserver-observer
    fi
    useradd -m "${XROAD_ADMIN_USER}" -s /usr/sbin/nologin -G xroad-securityserver-observer
    echo "${XROAD_ADMIN_USER}:${XROAD_ADMIN_PASSWORD}" | chpasswd
    echo "xroad-proxy xroad-common/username string ${XROAD_ADMIN_USER}" | debconf-set-selections
  fi
fi
XROAD_ADMIN_PASSWORD=

mkdir -p 1750 /var/tmp/xroad
chown xroad:xroad /var/lib/xroad /var/tmp/xroad
chown -R xroad:xroad /etc/xroad

log "Generating internal gRPC TLS keys and certificate"
rm -rf /var/run/xroad
mkdir -p -m0750 /var/run/xroad
chown xroad:xroad /var/run/xroad
su - xroad -c sh -c /usr/share/xroad/scripts/xroad-base.sh

#Try rsync until success
log "Syncing configuration from ${XROAD_PRIMARY_DNS}..."

RSYNC_CMD="rsync -q -e 'ssh -i /etc/.ssh/id_rsa -o StrictHostKeyChecking=no -o ConnectTimeout=5 ' -avz --timeout=10 --delete-delay --exclude /xroad.properties --exclude /gpghome --exclude '/conf.d/node.ini' --exclude '*.tmp' --delay-updates xroad-slave@${XROAD_PRIMARY_DNS}:/etc/xroad/ /etc/xroad/"

set -o pipefail
while :; do
 su -c "$RSYNC_CMD" xroad |& sed 's/^/    /' && break
 sleep 5 || exit 1
done

#Create cron job for rsync every minute
echo "* * * * * xroad $RSYNC_CMD 2>&1" > /etc/cron.d/xroad-state-sync
chown root:root /etc/cron.d/xroad-state-sync && chmod 644 /etc/cron.d/xroad-state-sync

log "Initial synchronization done, starting services"

# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
