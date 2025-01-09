
JMX_COMMON_PARAMS="-Dcom.sun.management.jmxremote=true -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=localhost"
JMX_OPTS="$JMX_COMMON_PARAMS -Dcom.sun.management.jmxremote.port=9990 -Dcom.sun.management.jmxremote.rmi.port=9990"

java \
  -Xdebug -agentlib:jdwp=transport=dt_socket,address=*:9999,server=y,suspend=n \
  $JMX_OPTS \
  -jar quarkus-run.jar
