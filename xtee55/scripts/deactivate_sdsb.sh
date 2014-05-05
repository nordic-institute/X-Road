#!/bin/sh
#
# Dectivates SDSB proxy.
#
# Reconfigures apache back from listening on localhost.
# Disables nginx site for client mediator.

. /etc/sdsb/services/global.conf

XTEE_ETC_DIR=/usr/xtee/etc
SDSB_SSL_DIR=/etc/sdsb/ssl
SITES_ENABLED_DIR=/etc/nginx/sites-enabled

XTEE_PROXY_MAKEFILE=Makefile.proxy
CLIENT_MEDIATOR_ENABLED_SITE=$SITES_ENABLED_DIR/xtee55-clientmediator
CLIENT_MEDIATOR_ENABLED_SSL_SITE=$SITES_ENABLED_DIR/xtee55-clientmediator-ssl

if [ -f $XTEE_ETC_DIR/sdsb_promoted ]; then
  echo ERROR: Cannot deactivate promoted SDSB proxy!
  exit 1
fi

if [ -f $XTEE_ETC_DIR/sdsb_activated ]; then
  rm -f $XTEE_ETC_DIR/sdsb_activated || exit 1
else
  echo SDSB proxy is not activated!
  exit 0
fi

echo Disable nginx site for client mediator..
rm -f $CLIENT_MEDIATOR_ENABLED_SITE
rm -f $CLIENT_MEDIATOR_ENABLED_SSL_SITE
service nginx restart


echo Reconfigure X-Road v5 apache web server..
if [ -f $XTEE_ETC_DIR/$XTEE_PROXY_MAKEFILE ]; then
  make -f $XTEE_PROXY_MAKEFILE -C $XTEE_ETC_DIR force-net || exit 1
else
  echo ERROR! Xtee proxy make file $XTEE_ETC_DIR/$XTEE_PROXY_MAKEFILE is missing.
  exit 1
fi

echo "SDSB proxy deactivated"
