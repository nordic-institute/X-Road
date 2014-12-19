#!/bin/sh
#
# Dectivates SDSB proxy.
#
# Reconfigures apache back from listening on localhost.
# Disables nginx site for client mediator.

. /etc/sdsb/services/global.conf

XTEE_ETC_DIR=/usr/xtee/etc
SDSB_SSL_DIR=/etc/sdsb/ssl

XTEE_PROXY_MAKEFILE=Makefile.proxy

if [ -f $XTEE_ETC_DIR/sdsb_promoted ]; then
  echo ERROR: Cannot deactivate promoted SDSB proxy!
  exit 1
fi

if [ -f $XTEE_ETC_DIR/sdsb_activated ]; then
  rm -f $XTEE_ETC_DIR/sdsb_activated || exit 1

echo Modifying local.ini 

/usr/share/sdsb/scripts/modify_inifile.py -f /etc/sdsb/conf.d/local.ini -s proxy -k server-listen-port -v 5501
/usr/share/sdsb/scripts/modify_inifile.py -f /etc/sdsb/conf.d/local.ini -s proxy -k ocsp-responder-port -v 5578
/usr/share/sdsb/scripts/modify_inifile.py -f /etc/sdsb/conf.d/local.ini -s proxy -k server-listen-address -v 127.0.0.1
/usr/share/sdsb/scripts/modify_inifile.py -f /etc/sdsb/conf.d/local.ini -s proxy -k ocsp-responder-listen-address -v 127.0.0.1

/usr/share/sdsb/scripts/modify_inifile.py -f /etc/sdsb/conf.d/local.ini -s client-mediator -k http-port -v 6668
/usr/share/sdsb/scripts/modify_inifile.py -f /etc/sdsb/conf.d/local.ini -s client-mediator -k https-port -v 6443

cat > /etc/nginx/sites-enabled/sdsb_proxy_disabled << EOF
# direct connection to proxy is disabled when v5.5 is not activated
server { listen 5577;
if (\$request_method = HEAD ) {
return 510;
} 
location / {
 proxy_pass http://localhost:5578;
}
}
server {listen 5500;
return 510;
}
EOF

else
  echo SDSB proxy is not activated!
  exit 0
fi

echo Restart services..
restart xtee55-clientmediator
restart xroad-proxy
restart nginx


echo Reconfigure X-Road v5 apache web server..
if [ -f $XTEE_ETC_DIR/$XTEE_PROXY_MAKEFILE ]; then
  make -f $XTEE_PROXY_MAKEFILE -C $XTEE_ETC_DIR force-net || exit 1
else
  echo ERROR! Xtee proxy make file $XTEE_ETC_DIR/$XTEE_PROXY_MAKEFILE is missing.
  exit 1
fi

echo "SDSB proxy deactivated"
