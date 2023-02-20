#!/bin/bash

# Update X-Road configuration on startup, if necessary
INSTALLED_VERSION=$(dpkg-query --showformat='${Version}' --show xroad-center)
PACKAGED_VERSION="$(cat /root/VERSION)"

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
        pg_ctlcluster 14 main start
        pg_isready -t 14
        dpkg-reconfigure xroad-center
        pg_ctlcluster 14 main stop
        nginx -s stop
        sleep 1
        echo "$PACKAGED_VERSION" >/etc/xroad/version
    fi
    if [ ! -f /home/ca/CA/.init ]; then
        echo "Initializing TEST-CA"
        su ca -c 'cd /home/ca/CA && ./init.sh'
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

exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
