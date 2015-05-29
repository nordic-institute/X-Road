#!/bin/bash

. /etc/xroad/services/global.conf

die () {
    echo >&2 "$@"
    exit 1
}


[ "$#" -eq 2 ] || die "trust anchor filename and configuration path required, $# provided"

CP="/usr/share/xroad/jlib/configuration-client.jar"


XROAD_LOG_LEVEL="INFO"

CONFCLIENT_PARAMS=" -Xmx50m -Dxroad.appLog.xroad.level=$XROAD_LOG_LEVEL "

${JAVA_HOME}/bin/java ${XROAD_PARAMS} ${CONFCLIENT_PARAMS} -cp ${CP} ee.ria.xroad.common.conf.globalconf.ConfigurationClientMain $@

