#!/bin/bash

. /etc/xroad/services/global.conf

die () {
    echo >&2 "$@"
    exit 1
}


[ "$#" -eq 3 ] || [ "$#" -eq 2 ] || die "trust anchor filename, configuration path and version required, $# provided"

CP="../configuration-client/configuration-client-application/build/libs/configuration-client-1.0.jar"

XROAD_CONFCLIENT_PARAMS=" -Xmx50m "

java ${XROAD_PARAMS} ${XROAD_CONFCLIENT_PARAMS} -cp ${CP} org.niis.xroad.confclient.ConfClientMain $@

