#!/bin/bash

XROAD_SCRIPT_LOCATION=/usr/share/xroad/scripts
DB_PROPERTIES=/etc/xroad/db.properties
GROUPNAMES="xroad-security-officer xroad-registration-officer xroad-service-administrator xroad-system-administrator xroad-securityserver-observer"

INSTALLED_VERSION=$(dpkg-query --showformat='${Version}' --show xroad-proxy)
PACKAGED_VERSION="$(cat /root/VERSION)"

# Update X-Road configuration on startup, if necessary
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
        pg_ctlcluster 10 main start
        pg_isready -t 10
        dpkg-reconfigure xroad-proxy
        pg_ctlcluster 10 main stop
        nginx -s stop
        sleep 1
        echo "$PACKAGED_VERSION" >/etc/xroad/version
    fi
else
    echo "WARN: Installed version ($INSTALLED_VERSION) does not match packaged version ($PACKAGED_VERSION)" >&2
fi

# Configure admin user with user-supplied username and password
user_exists=$(id -u ${XROAD_ADMIN_USER} > /dev/null 2>&1)
if [ $? != 0 ]
then
    echo "Creating admin user with user-supplied credentials"
    useradd -m ${XROAD_ADMIN_USER} -s /usr/sbin/nologin
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

# Generate internal and admin UI TLS keys and certificates on the first run
if [ ! -f /etc/xroad/ssl/internal.crt ];
then
    echo "Generating new internal TLS key and certificate"
    ARGS="-n internal -f -S -p"
    $XROAD_SCRIPT_LOCATION/generate_certificate.sh $ARGS
fi

if [ ! -f /etc/xroad/ssl/nginx.crt ];
then
    echo "Generating new SSL key and certificate for the admin UI"
    ARGS="-n nginx -f -S -p"
    $XROAD_SCRIPT_LOCATION/generate_certificate.sh $ARGS
fi

# Recreate serverconf database and properties file with serverconf username and random password
if [ ! -f ${DB_PROPERTIES} ]
then
    echo "Creating serverconf database and properties file"
    pg_ctlcluster 10 main start
    dpkg-reconfigure -fnoninteractive xroad-proxy
    pg_ctlcluster 10 main stop
    nginx -s stop
fi

# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
