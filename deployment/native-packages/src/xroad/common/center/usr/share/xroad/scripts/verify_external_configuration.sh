#!/bin/bash

. /etc/xroad/services/confclient.conf

die() {
  echo >&2 "$@"
  exit 1
}

[ "$#" -eq 1 ] || die "1 filename argument required, $# provided"

JAR="/usr/share/xroad/jlib/configuration-client.jar"

XROAD_CONFCLIENT_PARAMS="$XROAD_CONFCLIENT_PARAMS -Dquarkus.profile=cli,${XROAD_QUARKUS_PROFILES}"

exec java ${XROAD_PARAMS} ${XROAD_CONFCLIENT_PARAMS} -jar ${JAR} --verifyAnchorForExternalSource $@
