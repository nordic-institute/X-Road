#!/bin/sh
# Common entrypoint for Quarkus services

log() { echo "$(date --utc -Iseconds) INFO [entrypoint] $*"; }

DEBUG_OPTS=""
if [ "${DEBUG:-false}" = "true" ]; then
  log "DEBUG mode enabled - starting with JMX and debug agent"
  JMX_COMMON_PARAMS="-Dcom.sun.management.jmxremote=true -Dcom.sun.management.jmxremote.local.only=false \
   -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=localhost"
  JMX_OPTS="$JMX_COMMON_PARAMS -Dcom.sun.management.jmxremote.port=9990 -Dcom.sun.management.jmxremote.rmi.port=9990"
  DEBUG_AGENT="-Xdebug -agentlib:jdwp=transport=dt_socket,address=*:9999,server=y,suspend=n"
  DEBUG_OPTS="$DEBUG_AGENT $JMX_OPTS"
fi

exec java \
  -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
  -Dquarkus.profile=containerized \
  $DEBUG_OPTS \
  -jar /opt/app/quarkus-run.jar
