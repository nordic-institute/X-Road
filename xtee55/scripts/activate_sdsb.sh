#!/bin/sh
#
# Activates SDSB proxy.
#
# Reconfigures apache (X-Road v5 proxy) to listening on localhost.
# Imports clients, services, iternal SSL key and certificate
# from X-Road v5 proxy to SDBS proxy.
# Enables nginx sites for SDSB client mediator.
# (Re)Starts SDSB client and service mediators and SDSB proxy, etc..

XTEE_ETC_DIR=/usr/xtee/etc
SDSB_ETC_DIR=/etc/sdsb
SDSB_SCRIPTS_DIR=/usr/share/sdsb/scripts

. $SDSB_ETC_DIR/services/global.conf

XTEE_PROXY_MAKEFILE=Makefile.proxy
SERVICE_IMPORTER=$SDSB_SCRIPTS_DIR/serviceimporter.sh
IMPORT_INTERNAL_SSLKEY=$SDSB_SCRIPTS_DIR/import_internal_sslkey.sh
SDSB_CHECKER=$SDSB_SCRIPTS_DIR/check_sdsb.sh

SDSB_PROXY_UPSTART_CONF=/etc/init/xroad-proxy.conf
SDSB_SIGNER_UPSTART_CONF=/etc/init/xroad-signer.conf
SDSB_ASYNC_SENDER_UPSTART_CONF=/etc/init/xroad-async.conf
SDSB_DISTRIBUTED_FILES_CLIENT_UPSTART_CONF=/etc/init/xroad-confclient.conf
SDSB_JETTY_UPSTART_CONF=/etc/init/xroad-jetty.conf
XTEE55_CLIENT_MEDIATOR_UPSTART_CONF=/etc/init/xtee55-clientmediator.conf
XTEE55_SERVICE_MEDIATOR_UPSTART_CONF=/etc/init/xtee55-servicemediator.conf
XTEE55_MONITOR_AGENT_UPSTART_CONF=/etc/init/xtee55-monitor.conf


for REQUIRED_FILE in \
    $XTEE_ETC_DIR/$XTEE_PROXY_MAKEFILE \
    $SERVICE_IMPORTER \
    $IMPORT_INTERNAL_SSLKEY \
    $SDSB_CHECKER \
    $CLIENT_MEDIATOR_SITE \
    $CLIENT_MEDIATOR_SSL_SITE \
    $SDSB_PROXY_UPSTART_CONF \
    $SDSB_SIGNER_UPSTART_CONF \
    $SDSB_DISTRIBUTED_FILES_CLIENT_UPSTART_CONF \
    $SDSB_JETTY_UPSTART_CONF \
    $XTEE55_CLIENT_MEDIATOR_UPSTART_CONF \
    $XTEE55_SERVICE_MEDIATOR_UPSTART_CONF \
    $XTEE55_MONITOR_AGENT_UPSTART_CONF
do
  if [ ! -f $REQUIRED_FILE ]; then
      echo ERROR: Required file $REQUIRED_FILE is missing
    exit 1
  fi
done

echo Checking SDSB for minimal configuration..
su sdsb -c "$SDSB_CHECKER -checksdsb" || exit 1

if [ -d "$XTEE_ETC_DIR" ]; then
  if [ -f $XTEE_ETC_DIR/sdsb_activated ]; then
    echo Allready activated, reactivate
  else
    touch $XTEE_ETC_DIR/sdsb_activated || exit 1
    chmod a+r $XTEE_ETC_DIR/sdsb_activated || exit 1
  fi
else
  echo ERROR! Directory $XTEE_ETC_DIR not found
  exit 1
fi

echo Reconfigure apache web server..
make -f $XTEE_PROXY_MAKEFILE -C $XTEE_ETC_DIR force-net || exit 1

echo Modify local.ini for CM activation
/usr/share/sdsb/scripts/modify_inifile.py -f /etc/sdsb/conf.d/local.ini -s proxy -k server-listen-port -v 5500
/usr/share/sdsb/scripts/modify_inifile.py -f /etc/sdsb/conf.d/local.ini -s proxy -k ocsp-responder-port -v 5577
/usr/share/sdsb/scripts/modify_inifile.py -f /etc/sdsb/conf.d/local.ini -s proxy -k server-listen-address -v 0.0.0.0
/usr/share/sdsb/scripts/modify_inifile.py -f /etc/sdsb/conf.d/local.ini -s proxy -k ocsp-responder-listen-address -v 0.0.0.0

/usr/share/sdsb/scripts/modify_inifile.py -f /etc/sdsb/conf.d/local.ini -s client-mediator -k http-port -v 80
/usr/share/sdsb/scripts/modify_inifile.py -f /etc/sdsb/conf.d/local.ini -s client-mediator -k https-port -v 443

echo Import clients and services from X-Road proxy..
su sdsb -c "$SERVICE_IMPORTER" || exit 1

echo Import internal SSL key and certificate from X-Road proxy..
$IMPORT_INTERNAL_SSLKEY -no-reload || exit 1

echo \(Re\)Start SDSB signer..
restart xroad-signer || start xroad-signer

if [ -f $SDSB_ASYNC_SENDER_UPSTART_CONF ]; then
  echo \(Re\)Start SDSB async sender..
  restart xroad-async || start xroad-async
fi

echo \(Re\)Start SDSB distributed files client..
restart xroad-confclient || start xroad-confclient

echo \(Re\)Start jetty server..
restart xroad-jetty || start xroad-jetty

echo \(Re\)Start client mediator..
restart xtee55-clientmediator || start xtee55-clientmediator

echo \(Re\)Start service mediator..
restart xtee55-servicemediator || start xtee55-servicemediator

echo \(Re\)Start monitor agent..
restart xtee55-monitor || start xtee55-monitor

echo Disable v6 activate checking responder
rm /etc/nginx/sites-enabled/sdsb_proxy_disabled
service nginx restart

echo \(Re\)Start SDSB proxy..
restart xroad-proxy || start xroad-proxy

echo "SDSB proxy activated"
