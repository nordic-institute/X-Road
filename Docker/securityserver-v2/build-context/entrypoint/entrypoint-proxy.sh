#!/bin/bash

log() { echo "$(date --utc -Iseconds) INFO [entrypoint] $*"; }

XROAD_SCRIPT_LOCATION=/opt/app/scripts

# Generate internal and admin UI TLS keys and certificates if necessary
if [ ! -f /etc/xroad/ssl/internal.crt ]; then
  log "Generating new internal TLS key and certificate"
  "$XROAD_SCRIPT_LOCATION/generate_certificate.sh" -c /opt/app/scripts/ -n internal -f -S -p 2>&1 >/dev/null | sed 's/^/    /'
fi

if [ ! -f /etc/xroad/ssl/proxy-ui-api.crt ]; then
  log "Generating new SSL key and certificate for the admin UI"
  "$XROAD_SCRIPT_LOCATION/generate_certificate.sh" -c /opt/app/scripts/ -n proxy-ui-api -f -S -p 2>&1 >/dev/null | sed 's/^/   /'
fi

exec java -jar /opt/app/application.jar