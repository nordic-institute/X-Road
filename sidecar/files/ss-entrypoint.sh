#!/bin/bash

XROAD_SCRIPT_LOCATION=/usr/share/xroad/scripts
DB_PROPERTIES=/etc/xroad/db.properties
DB_URL=jdbc:postgresql://127.0.0.1:5432/serverconf
DB_NAME=serverconf

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

# Set xroad-autologin software token PIN code
if [  -n "$XROAD_TOKEN_PIN" ]
then
    echo "XROAD_TOKEN_PIN variable set, writing to /etc/xroad/autologin"
    su xroad -c 'echo $XROAD_TOKEN_PIN >/etc/xroad/autologin'
    unset XROAD_TOKEN_PIN
fi

# Recreate serverconf database and properties file with user-supplied username and password
echo "Creating serverconf database with user-supplied credentials"
pg_ctlcluster 10 main start
su - postgres -c "psql postgres -tAc \"CREATE ROLE ${XROAD_DB_USER} LOGIN PASSWORD '${XROAD_DB_PASSWD}';\""
su - postgres -c "createdb ${DB_NAME} -O ${XROAD_DB_USER} -E UTF-8"
su - postgres -c "psql ${DB_NAME} -tAc \"CREATE EXTENSION IF NOT EXISTS hstore\""
cd /usr/share/xroad/db/
/usr/share/xroad/db/liquibase.sh --classpath=/usr/share/xroad/jlib/proxy.jar --url="${DB_URL}?dialect=ee.ria.xroad.common.db.CustomPostgreSQLDialect" --changeLogFile=/usr/share/xroad/db/${DB_NAME}-changelog.xml --password=${XROAD_DB_PASSWD} --username=${XROAD_DB_USER}  update || die "Connection to database has failed, please check database availability and configuration in ${DB_PROPERTIES} file"
pg_ctlcluster 10 main stop

touch $DB_PROPERTIES
crudini --set $DB_PROPERTIES '' serverconf.hibernate.jdbc.use_streams_for_binary true
crudini --set $DB_PROPERTIES '' serverconf.hibernate.dialect ee.ria.xroad.common.db.CustomPostgreSQLDialect
crudini --set $DB_PROPERTIES '' serverconf.hibernate.connection.driver_class org.postgresql.Driver
crudini --set $DB_PROPERTIES '' serverconf.hibernate.connection.url $DB_URL
crudini --set $DB_PROPERTIES '' serverconf.hibernate.connection.username  $XROAD_DB_USER
crudini --set $DB_PROPERTIES '' serverconf.hibernate.connection.password $XROAD_DB_PASSWD
chown xroad:xroad $DB_PROPERTIES
chmod 640 $DB_PROPERTIES

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

# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
