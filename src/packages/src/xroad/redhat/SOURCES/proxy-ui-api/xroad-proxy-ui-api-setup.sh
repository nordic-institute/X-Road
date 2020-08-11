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

if [[ -f /etc/xroad/ssl/nginx.crt && -f /etc/xroad/ssl/nginx.key ]];
then
  if [[ ! -r /etc/xroad/ssl/proxy-ui-api.crt || ! -r /etc/xroad/ssl/proxy-ui-api.key || ! -r /etc/xroad/ssl/proxy-ui-api.p12 ]]
  then
    echo "found existing nginx.crt and nginx.key, migrating those to proxy-ui-api.crt, key and p12"
    mv -f /etc/xroad/ssl/nginx.crt /etc/xroad/ssl/proxy-ui-api.crt
    mv -f /etc/xroad/ssl/nginx.key /etc/xroad/ssl/proxy-ui-api.key
    rm /etc/xroad/ssl/proxy-ui-api.p12
    openssl pkcs12 -export -in /etc/xroad/ssl/proxy-ui-api.crt -inkey /etc/xroad/ssl/proxy-ui-api.key -name proxy-ui-api -out /etc/xroad/ssl/proxy-ui-api.p12 -passout pass:proxy-ui-api
    chmod -f 660 /etc/xroad/ssl/proxy-ui-api.key /etc/xroad/ssl/proxy-ui-api.crt /etc/xroad/ssl/proxy-ui-api.p12
    chown -f xroad:xroad /etc/xroad/ssl/proxy-ui-api.key /etc/xroad/ssl/proxy-ui-api.crt /etc/xroad/ssl/proxy-ui-api.p12

  else
    echo "found existing proxy-ui-api.key, crt and p12, keeping those and not migrating nginx.key and crt"
  fi
fi

if [[ ! -r /etc/xroad/ssl/proxy-ui-api.crt || ! -r /etc/xroad/ssl/proxy-ui-api.key  || ! -r /etc/xroad/ssl/proxy-ui-api.p12 ]]
then
    echo "Generating new proxy-ui-api.[crt|key|p12] files "
    rm -f /etc/xroad/ssl/proxy-ui-api.crt /etc/xroad/ssl/proxy-ui-api.key /etc/xroad/ssl/proxy-ui-api.p12
    /usr/share/xroad/scripts/generate_certificate.sh  -n proxy-ui-api -s "/CN=$HOST" -a "$ALT" -p 2> /tmp/cert.err || handle_error
fi
