#!/bin/sh
#
# Promote X-Road 6.0 proxy.
#
# Imports X-Road v5 proxy clients, services, internal SSL key and certificate
# to X-Road 6.0 proxy (just in case).
# Disables configuring services and internal SSL key on X-Road v5 proxy.
# Enables configuring services and internal SSL key on X-Road 6.0 proxy.
# Exports from X-Road 6.0 proxy clients, services, internal SSL key and certificate
# to X-Road v5 proxy (X-Road v5 proxy will use service mediator).

. /etc/xroad/services/global.conf

XTEE_ETC_DIR=/usr/xtee/etc
XROAD_SCRIPTS_DIR=/usr/share/xroad/scripts

SERVICE_IMPORTER=$XROAD_SCRIPTS_DIR/serviceimporter.sh
SERVICE_EXPORTER=$XROAD_SCRIPTS_DIR/serviceexporter.sh
IMPORT_INTERNAL_SSLKEY=$XROAD_SCRIPTS_DIR/import_internal_sslkey.sh
XROAD_CHECKER=$XROAD_SCRIPTS_DIR/check_v6_xroad.sh

for REQUIRED_FILE in \
    $SERVICE_IMPORTER \
    $SERVICE_EXPORTER \
    $IMPORT_INTERNAL_SSLKEY
do
  if [ ! -f $REQUIRED_FILE ]; then
    echo ERROR: Required file $REQUIRED_FILE is missing
    exit 1
  fi
done

echo Checking X-Road 6.0 for minimal configuration..
su xroad -c "$XROAD_CHECKER -checkpromote" || exit 1

if [ -f $XTEE_ETC_DIR/v6_xroad_promoted ]; then
  echo X-Road 6.0 proxy allready promoted, repromote
elif [ -f $XTEE_ETC_DIR/v6_xroad_activated ]; then
  echo Import clients and services from X-Road v5 proxy..
  su xroad -c "$SERVICE_IMPORTER" || exit 1

  echo Import internal SSL key and certificate from X-Road v5 proxy..
  $IMPORT_INTERNAL_SSLKEY || exit 1

  touch $XTEE_ETC_DIR/v6_xroad_promoted || exit 1
  chmod a+r $XTEE_ETC_DIR/v6_xroad_promoted || exit 1
else
  echo ERROR: Cannot promote, X-Road 6.0 proxy is not activated yet!
  exit 1
fi

echo Export clients and services to X-Road v5 proxy..
su ui -c "$SERVICE_EXPORTER" || exit 1

# No need to export internal SSL key and certificate to X-Road v5 proxy.

echo "X-Road 6.0 proxy promoted"
