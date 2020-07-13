#!/bin/bash
# X-Road proxy UI API post-install configuration

#
# Create default internal certificates for xroad-proxy-ui-api

HOST=$(hostname -f)
LIST=
for i in $(ip addr | grep 'scope global' | tr '/' ' ' | awk '{print $2}')
do
    LIST+="IP:$i,";
done

ALT="${LIST}DNS:$(hostname),DNS:$HOSTNAME"

if [[ ! -r /etc/xroad/ssl/proxy-ui-api.crt || ! -r /etc/xroad/ssl/proxy-ui-api.key  || ! -r /etc/xroad/ssl/proxy-ui-api.p12 ]]
then
    echo "Generating new proxy-ui-api.[crt|key|p12] files "
    rm -f /etc/xroad/ssl/proxy-ui-api.crt /etc/xroad/ssl/proxy-ui-api.key /etc/xroad/ssl/proxy-ui-api.p12
    /usr/share/xroad/scripts/generate_certificate.sh  -n proxy-ui-api -s "/CN=$HOST" -a "$ALT" -p 2> /tmp/cert.err || handle_error
fi
