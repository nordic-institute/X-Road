#!/bin/bash

log() { echo "$(date --utc -Iseconds) INFO [entrypoint] $*"; }

DEBUG_OPTS=""
if [[ "${DEBUG:-false}" == "true" ]]; then
  log "DEBUG mode enabled - starting with JMX and debug agent"
  JMX_COMMON_PARAMS="-Dcom.sun.management.jmxremote=true -Dcom.sun.management.jmxremote.local.only=false \
   -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=localhost"
  JMX_OPTS="$JMX_COMMON_PARAMS -Dcom.sun.management.jmxremote.port=9990 -Dcom.sun.management.jmxremote.rmi.port=9990"
  
  # DEBUG_SUSPEND controls whether the JVM suspends until debugger attaches (y/n, default: n)
  DEBUG_SUSPEND="${DEBUG_SUSPEND:-n}"
  log "Debug suspend mode: ${DEBUG_SUSPEND}"
  DEBUG_AGENT="-Xdebug -agentlib:jdwp=transport=dt_socket,address=*:9999,server=y,suspend=${DEBUG_SUSPEND}"
  DEBUG_OPTS="$DEBUG_AGENT $JMX_OPTS"
fi

for cert in /usr/local/share/ca-certificates/*.crt; do
    alias_name=$(basename "$cert" .crt)
    keytool -importcert \
        -trustcacerts \
        -file "$cert" \
        -alias "$alias_name" \
        -keystore "$JAVA_HOME/lib/security/cacerts" \
        -storepass changeit \
        -noprompt
done

exec java \
  $DEBUG_OPTS \
  -Dspring.profiles.include=containerized \
  -jar /opt/app/proxy-ui-api.jar

