#!/bin/bash

log() { echo "$(date --utc -Iseconds) INFO [entrypoint] $*"; }

# Update X-Road configuration on startup, if necessary
INSTALLED_VERSION=$(dpkg-query --showformat='${Version}' --show xroad-proxy)
PACKAGED_VERSION="$(cat /root/VERSION)"

log "Starting X-Road security server version $INSTALLED_VERSION"

if [ "$INSTALLED_VERSION" == "$PACKAGED_VERSION" ]; then
    if [ -f /etc/xroad/VERSION ]; then
        CONFIG_VERSION="$(cat /etc/xroad/VERSION)"
    else
        echo "WARN: Current configuration version not known" >&2
        CONFIG_VERSION=
    fi
    if [ -n "$CONFIG_VERSION" ] && dpkg --compare-versions "$PACKAGED_VERSION" gt "$CONFIG_VERSION"; then
        echo "Updating configuration from $CONFIG_VERSION to $PACKAGED_VERSION"
        cp -a /root/etc/xroad/* /etc/xroad/
        pg_ctlcluster 16 main start
        pg_isready -t 16
        dpkg-reconfigure xroad-proxy xroad-signer xroad-addon-messagelog
        pg_ctlcluster 16 main stop
        sleep 1
        echo "$PACKAGED_VERSION" >/etc/xroad/version
    fi
else
    echo "WARN: Installed version ($INSTALLED_VERSION) does not match packaged version ($PACKAGED_VERSION)" >&2
fi

if [  -n "$XROAD_TOKEN_PIN" ]
then
    echo "XROAD_TOKEN_PIN variable set, writing to /etc/xroad/autologin"
    echo "$XROAD_TOKEN_PIN" > /etc/xroad/autologin
    unset XROAD_TOKEN_PIN
fi

log "Enabling public postgres access.."
sed -i 's/#listen_addresses = \x27localhost\x27/listen_addresses = \x27*\x27/g' /etc/postgresql/*/main/postgresql.conf
sed -ri 's/host    replication     all             127.0.0.1\/32/host    all             all             0.0.0.0\/0/g' /etc/postgresql/*/main/pg_hba.conf

log "initializing transport keys"
mkdir -p -m0750 /var/run/xroad
chown xroad:xroad /var/run/xroad
su - xroad -c sh -c /usr/share/xroad/scripts/xroad-base.sh

exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
