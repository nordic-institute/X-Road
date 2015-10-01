#!/bin/sh
#
# Activates X-Road 6.0 proxy.
#
# Reconfigures apache (X-Road v5 proxy) to listening on localhost.
# Reconfigures client mediator to listening ports 80 and 433.
# Imports internal TLS key and certificate from X-Road 5.0.
# (Re)Starts X-Road 6.0 client mediator, proxy, etc..

if [ "$(id -nu )" != "root" ]
then
  echo "ABORTED. This script must run under root user" >&2
  exit 1
fi


XTEE_ETC_DIR=/usr/xtee/etc
XROAD_ETC_DIR=/etc/xroad
XROAD_SCRIPTS_DIR=/usr/share/xroad/scripts

. $XROAD_ETC_DIR/services/global.conf

XTEE_PROXY_MAKEFILE=Makefile.proxy

XROAD_PROXY_UPSTART_CONF=/etc/init/xroad-proxy.conf
XROAD_SIGNER_UPSTART_CONF=/etc/init/xroad-signer.conf
XROAD_CONFIGURATION_FILES_CLIENT_UPSTART_CONF=/etc/init/xroad-confclient.conf
XROAD_JETTY_UPSTART_CONF=/etc/init/xroad-jetty.conf
XTEE55_CLIENT_MEDIATOR_UPSTART_CONF=/etc/init/xtee55-clientmediator.conf
XTEE55_MONITOR_AGENT_UPSTART_CONF=/etc/init/xtee55-monitor.conf
IMPORT_INTERNAL_TLS_KEY=$XROAD_SCRIPTS_DIR/import_v5_internal_tls_key.sh


for REQUIRED_FILE in \
    $XTEE_ETC_DIR/$XTEE_PROXY_MAKEFILE \
    $XROAD_PROXY_UPSTART_CONF \
    $XROAD_SIGNER_UPSTART_CONF \
    $XROAD_CONFIGURATION_FILES_CLIENT_UPSTART_CONF \
    $XROAD_JETTY_UPSTART_CONF \
    $XTEE55_CLIENT_MEDIATOR_UPSTART_CONF \
    $XTEE55_MONITOR_AGENT_UPSTART_CONF \
    $IMPORT_INTERNAL_TLS_KEY
do
  if [ ! -f $REQUIRED_FILE ]; then
      echo ERROR: Required file $REQUIRED_FILE is missing
    exit 1
  fi
done

if [ -d "$XTEE_ETC_DIR" ]; then
  if [ -f $XTEE_ETC_DIR/v6_xroad_activated ]; then
    echo Allready activated, reactivate
  else
    touch $XTEE_ETC_DIR/v6_xroad_activated || exit 1
    chmod a+r $XTEE_ETC_DIR/v6_xroad_activated || exit 1
  fi
else
  echo ERROR! Directory $XTEE_ETC_DIR not found
  exit 1
fi


echo Reconfigure apache web server..
make -f $XTEE_PROXY_MAKEFILE -C $XTEE_ETC_DIR force-net || exit 1


echo Modify local.ini for CM activation
/usr/share/xroad/scripts/modify_inifile.py -f /etc/xroad/conf.d/local.ini -s client-mediator -k http-port -v 80 || exit 1
/usr/share/xroad/scripts/modify_inifile.py -f /etc/xroad/conf.d/local.ini -s client-mediator -k https-port -v 443 || exit 1

echo Import internal TLS key and certificate from 5.0 X-Road proxy..
$IMPORT_INTERNAL_TLS_KEY -no-reload || exit 1

echo \(Re\)Start X-Road 6.0 signer..
restart xroad-signer || start xroad-signer

echo \(Re\)Start X-Road 6.0 configuration files client..
restart xroad-confclient || start xroad-confclient

echo \(Re\)Start X-Road 6.0 jetty server..
restart xroad-jetty || start xroad-jetty

echo \(Re\)Start X-Road 5.5 client mediator..
restart xtee55-clientmediator || start xtee55-clientmediator

echo \(Re\)Start X-Road 5.5 monitor agent..
restart xtee55-monitor || start xtee55-monitor

echo \(Re\)Start X-Road 6.0 proxy..
restart xroad-proxy || start xroad-proxy

echo "X-Road 6.0 activated"
