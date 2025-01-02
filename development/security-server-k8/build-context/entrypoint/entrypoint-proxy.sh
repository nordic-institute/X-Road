#!/bin/bash

log() { echo "$(date --utc -Iseconds) INFO [entrypoint] $*"; }

XROAD_SCRIPT_LOCATION=/opt/app/scripts

# Generate internal and admin UI TLS keys and certificates if necessary
log "Generating new internal TLS key and certificate"
"$XROAD_SCRIPT_LOCATION/generate_certificate.sh" -c /opt/app/scripts/ -n internal -f -S -p 2>&1 >/dev/null | sed 's/^/    /'

JVM_OPTS="-Xdebug -agentlib:jdwp=transport=dt_socket,address=*:9999,server=y,suspend=n"
exec java $JVM_OPTS -jar /opt/app/application.jar
