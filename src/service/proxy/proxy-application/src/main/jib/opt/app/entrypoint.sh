log() { echo "$(date --utc -Iseconds) INFO [entrypoint] $*"; }

XROAD_SCRIPT_LOCATION=/opt/app/scripts

JMX_COMMON_PARAMS="-Dcom.sun.management.jmxremote=true -Dcom.sun.management.jmxremote.local.only=false \
 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=localhost"
JMX_OPTS="$JMX_COMMON_PARAMS -Dcom.sun.management.jmxremote.port=9990 -Dcom.sun.management.jmxremote.rmi.port=9990"

exec java \
  -Xdebug -agentlib:jdwp=transport=dt_socket,address=*:9999,server=y,suspend=n \
  -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
  -Dquarkus.profile=containerized \
  $JMX_OPTS \
  -jar /opt/app/quarkus-run.jar
