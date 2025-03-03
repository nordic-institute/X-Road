#!/bin/bash

log() { echo "$(date --utc -Iseconds) INFO [entrypoint] $*"; }

XROAD_SCRIPT_LOCATION=/opt/app/scripts

# Generate internal and admin UI TLS keys and certificates if necessary
log "Generating new SSL key and certificate for the admin UI"
"$XROAD_SCRIPT_LOCATION/generate_certificate.sh" -c $XROAD_SCRIPT_LOCATION -n proxy-ui-api -f -S -p 2>&1 >/dev/null | sed 's/^/   /'

JMX_COMMON_PARAMS="-Dcom.sun.management.jmxremote=true -Dcom.sun.management.jmxremote.local.only=false \
 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=localhost"
JMX_OPTS="$JMX_COMMON_PARAMS -Dcom.sun.management.jmxremote.port=9990 -Dcom.sun.management.jmxremote.rmi.port=9990"

java \
  -Xdebug -agentlib:jdwp=transport=dt_socket,address=*:9999,server=y,suspend=n \
  $JMX_OPTS \
  -Dspring.profiles.include=containerized \
  -cp @jib-classpath-file @jib-main-class-file
