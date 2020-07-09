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

if [[ ! -r /etc/xroad/ssl/proxy-ui-api.crt || ! -r /etc/xroad/ssl/proxy-ui-api.key ]]
then
    echo "Generating new proxy-ui-api.[crt|key] files "
    rm -f /etc/xroad/ssl/proxy-ui-api.crt /etc/xroad/ssl/proxy-ui-api.key
    /bin/bash /usr/share/xroad/scripts/generate_certificate.sh  -n proxy-ui-api  -s "/CN=$HOST" -a "$ALT" 2>/tmp/cert.err
fi
