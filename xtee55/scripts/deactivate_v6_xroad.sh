#!/bin/sh
#
# Dectivates X-Road 6.0 proxy.
#
# Reconfigures client mediator back from listening on ports 80 and 433.
# Reconfigures apache back from listening on localhost.

if [ "$(id -nu )" != "root" ]
then
  echo "ABORTED. This script must run under root user" >&2
  exit 1
fi


. /etc/xroad/services/global.conf

XTEE_ETC_DIR=/usr/xtee/etc

XTEE_PROXY_MAKEFILE=Makefile.proxy

if [ -f $XTEE_ETC_DIR/v6_xroad_activated ]; then
  rm -f $XTEE_ETC_DIR/v6_xroad_activated || exit 1

  echo Modifying local.ini
  /usr/share/xroad/scripts/modify_inifile.py -f /etc/xroad/conf.d/local.ini -s client-mediator -k http-port -v 6668
  /usr/share/xroad/scripts/modify_inifile.py -f /etc/xroad/conf.d/local.ini -s client-mediator -k https-port -v 6443
else
  echo X-Road 6.0 proxy is not activated!
  exit 0
fi

echo Restart services..
restart xtee55-clientmediator

echo Reconfigure X-Road v5 apache web server..
if [ -f $XTEE_ETC_DIR/$XTEE_PROXY_MAKEFILE ]; then
  make -f $XTEE_PROXY_MAKEFILE -C $XTEE_ETC_DIR force-net || exit 1
else
  echo ERROR! Xtee proxy make file $XTEE_ETC_DIR/$XTEE_PROXY_MAKEFILE is missing.
  exit 1
fi

echo "X-Road 6.0 proxy deactivated"
