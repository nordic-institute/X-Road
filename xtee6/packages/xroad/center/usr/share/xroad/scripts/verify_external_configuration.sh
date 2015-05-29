#!/bin/bash

. /etc/xroad/services/global.conf

die () {
    echo >&2 "$@"
    exit 1
}


[ "$#" -eq 1 ] || die "1 filename argument required, $# provided"

CP="/usr/share/xroad/jlib/configuration-client.jar"


XROAD_LOG_LEVEL="INFO"

CONFCLIENT_PARAMS=" -Xmx50m -XX:MaxMetaspaceSize=70m \
-Dxroad.appLog.xroad.level=$XROAD_LOG_LEVEL "


${JAVA_HOME}/bin/java ${XROAD_PARAMS} ${CONFCLIENT_PARAMS} -Dlogback.configurationFile=/etc/xroad/conf.d/confclient-logback.xml -cp /usr/share/xroad/jlib/configuration-client.jar  ee.ria.xroad.common.conf.globalconf.ConfigurationClientMain $@

