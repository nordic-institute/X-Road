#!/bin/bash
# X-Road proxy post-install configuration
die () {
    echo >&2 "$@"
    exit 1
}
#
# Create default internal certificates for nginx
#
HOST=$(hostname -f)
LIST=
for i in $(ip addr | grep 'scope global' | tr '/' ' ' | awk '{print $2}')
do
    LIST+="IP:$i,";
done

ALT=${LIST}DNS:$(hostname),DNS:$HOST

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

mkdir -p /var/spool/xroad && chown xroad:xroad /var/spool/xroad
mkdir -p /var/cache/xroad && chown xroad:xroad /var/cache/xroad
mkdir -p /etc/xroad/globalconf && chown xroad:xroad /etc/xroad/globalconf

#
# Database migrations (optional db setup in xroad-initdb)
#
db_name=serverconf
db_properties=/etc/xroad/db.properties
db_url=$(crudini --get ${db_properties} '' serverconf.hibernate.connection.url)
db_user=$(crudini --get ${db_properties} '' serverconf.hibernate.connection.username)
db_passwd=$(crudini --get ${db_properties} '' serverconf.hibernate.connection.password)
db_admin_properties=/etc/xroad/db-admin.properties

node_type=$(crudini --get '/etc/xroad/conf.d/node.ini' node type 2>/dev/null || echo standalone)

if [[ "$node_type" == "slave" ]]; then
    echo "Skipping database migrations on a slave node"
else
    cd /usr/share/xroad/db/ || die "Cannot run DB migrations"
    echo "running ${db_name} database migrations"

    #separate admin properties
    if [[ -f "$db_admin_properties" ]]; then
      db_admin_user=$(crudini --get "$db_admin_properties" '' 'serverconf.admin.username' || echo "$db_user")
      db_admin_password=$(crudini --get "$db_admin_properties" '' 'serverconf.admin.password' || echo "$db_passwd")
    fi

    context=""
    if [[ "$db_user" != "$db_admin_user" ]]; then
      context="--contexts=admin"
    fi

    JAVA_OPTS="-Ddb_user=$db_user" /usr/share/xroad/db/liquibase.sh \
      --classpath=/usr/share/xroad/jlib/proxy.jar \
      --url="${db_url}?dialect=ee.ria.xroad.common.db.CustomPostgreSQLDialect" \
      --changeLogFile=/usr/share/xroad/db/${db_name}-changelog.xml \
      --password="${db_admin_password}" \
      --username="${db_admin_user}" \
      $context \
      update \
      || die "Connection to database has failed, please check database availability and configuration in ${db_properties} file"
fi

#
# SELinux policy modification
#
if [[ $(getenforce) != "Disabled" ]]; then
    # allow httpd to act as reverse proxy
    setsebool -P httpd_can_network_relay 1 || true
    setsebool -P httpd_can_network_connect 1 || true

    # allow httpd to connecto to non-standard port 4000
    semanage port -a -t http_port_t  -p tcp 4000 || true
fi
