#!/bin/sh
# X-Road proxy post-install configuration

#
# Create default internal certificates for nginx
#
HOST=`hostname -f`
LIST=
for i in `ip addr | grep 'scope global' | tr '/' ' ' | awk '{print $2}'`
do
    LIST+="IP:$i,";
done

ALT=${LIST}DNS:`hostname`,DNS:`hostname -f`

if [[ ! -r /etc/xroad/ssl/nginx.crt || ! -r /etc/xroad/ssl/nginx.key ]]
then
    echo "Generating new nginx.[crt|key] files "
    rm -f /etc/xroad/ssl/nginx.crt /etc/xroad/ssl/nginx.key
    /bin/bash /usr/share/xroad/scripts/generate_certificate.sh  -n nginx  -s "/CN=$HOST" -a "$ALT" 2>/tmp/cert.err
fi

if [[ ! -r /etc/xroad/ssl/internal.crt || ! -r /etc/xroad/ssl/internal.key  || ! -r /etc/xroad/ssl/internal.p12 ]]
then
    echo "Generating new internal.[crt|key|p12] files "
    rm -f /etc/xroad/ssl/internal.crt /etc/xroad/ssl/internal.key /etc/xroad/ssl/internal.p12
    /usr/share/xroad/scripts/generate_certificate.sh  -n internal -s "/CN=$HOST" -a "$ALT" -p 2> /tmp/cert.err || handle_error
fi

test -d /var/spool/xroad && test -w /var/spool/xroad || mkdir /var/spool/xroad ; chown xroad:xroad /var/spool/xroad
test -d /var/cache/xroad && test -w /var/cache/xroad || mkdir /var/cache/xroad ; chown xroad:xroad /var/cache/xroad
test -d /etc/xroad/globalconf && test -w /etc/xroad/globalconf || mkdir /etc/xroad/globalconf ; chown xroad:xroad  /etc/xroad/globalconf

die () {
    echo >&2 "$@"
    exit 1
}

#
# Database migrations (optional db setup in xroad-initdb)
#
db_name=serverconf
db_properties=/etc/xroad/db.properties
db_url=`crudini --get ${db_properties} '' serverconf.hibernate.connection.url`
db_user=`crudini --get ${db_properties} '' serverconf.hibernate.connection.username`
db_passwd=`crudini --get ${db_properties} '' serverconf.hibernate.connection.password`

echo "running ${db_name} database migrations"
cd /usr/share/xroad/db/
/usr/share/xroad/db/liquibase --classpath=/usr/share/xroad/jlib/proxy.jar --url="${db_url}?dialect=ee.ria.xroad.common.db.CustomPostgreSQLDialect" --changeLogFile=/usr/share/xroad/db/${db_name}-changelog.xml --password=${db_passwd} --username=${db_user}  update || die "Connection to database has failed, please check database availability and configuration ad ${db_properties} file"

#
# SELinux policy modification
#

# allow httpd to act as reverse proxy
setsebool -P httpd_can_network_relay 1 || true
setsebool -P httpd_can_network_connect 1 || true

# allow httpd to connecto to non-standard port 4000
semanage port -a -t http_port_t  -p tcp 4000 || true

