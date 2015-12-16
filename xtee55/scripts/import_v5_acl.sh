#!/bin/sh

if [ "$(id -nu )" != "xroad" ]
then
  echo "ABORTED. This script must run under xroad user" >&2
  exit 1
fi

. /etc/xroad/services/xtee55-confimporter.conf

${JAVA_HOME}/bin/java ${XROAD_PARAMS} ${X55_IMPORTER_PARAMS} -jar /usr/share/xroad/jlib/confimporter.jar -acl
