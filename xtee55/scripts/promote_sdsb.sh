#!/bin/sh
#
# Promote SDSB proxy.
#
# Imports X-Road v5 proxy clients, services, internal SSL key and certificate
# to SDSB proxy (just in case).
# Disables configuring services and internal SSL key on X-Road v5 proxy.
# Enables configuring services and internal SSL key on SDSB proxy.
# Exports from SDSB proxy clients, services, internal SSL key and certificate
# to X-Road v5 proxy (X-Road v5 proxy will use service mediator).

. /etc/sdsb/services/global.conf

XTEE_ETC_DIR=/usr/xtee/etc
SDSB_SCRIPTS_DIR=/usr/share/sdsb/scripts

SERVICE_IMPORTER=$SDSB_SCRIPTS_DIR/serviceimporter.sh
SERVICE_EXPORTER=$SDSB_SCRIPTS_DIR/serviceexporter.sh
IMPORT_INTERNAL_SSLKEY=$SDSB_SCRIPTS_DIR/import_internal_sslkey.sh

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

if [ -f $XTEE_ETC_DIR/sdsb_promoted ]; then
  echo SDSB proxy allready promoted, repromote
elif [ -f $XTEE_ETC_DIR/sdsb_activated ]; then
  echo Import clients and services from X-Road proxy..
  su sdsb -c "$SERVICE_IMPORTER" || exit 1

  echo Import internal SSL key and certificate from X-Road proxy..
  $IMPORT_INTERNAL_SSLKEY || exit 1

  touch $XTEE_ETC_DIR/sdsb_promoted || exit 1
  chmod a+r $XTEE_ETC_DIR/sdsb_promoted || exit 1
else
  echo ERROR: Cannot promote, SDSB proxy is not activated yet!
  exit 1
fi

echo Export clients and services to X-Road v5 proxy..
su ui -c "$SERVICE_EXPORTER" || exit 1

# No need to export internal SSL key and certificate to X-Road v5 proxy.

echo "SDSB proxy promoted"
