#!/bin/bash
# X-Road proxy post-install configuration

#
# Create default internal certificates for nginx
#
HOST=$(hostname -f)
LIST=
for i in $(ip addr | grep 'scope global' | tr '/' ' ' | awk '{print $2}')
do
    LIST+="IP:$i,";
done

ALT="${LIST}DNS:$(hostname),DNS:$HOSTNAME"

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

mkdir -p /var/spool/xroad; chown xroad:xroad /var/spool/xroad
mkdir -p /var/cache/xroad; chown xroad:xroad /var/cache/xroad
mkdir -p /etc/xroad/globalconf; chown xroad:xroad /etc/xroad/globalconf

#
# SELinux policy modification
#
if [[ $(getenforce) != "Disabled" ]]; then
    # allow httpd to act as reverse proxy
    setsebool -P httpd_can_network_relay 1 || true
    setsebool -P httpd_can_network_connect 1 || true

    # allow httpd to connecto to non-standard port 4000
    semanage port -a -t http_port_t  -p tcp 4000 || true

    # allow httpd to connect to non-standard port 5000 (keep this as long as we have the dual old & new UI)
    if ! semanage port -m -t http_port_t  -p tcp 5000
    then
        echo "could not modify SELinux port 5000, will add instead"
        semanage port -a -t http_port_t  -p tcp 5000 || true
    fi

fi

/usr/share/xroad/scripts/setup_serverconf_db.sh
