#!/bin/bash

. /etc/xroad/services/global.conf

die () {
    echo >&2 "$@"
    exit 1
}


[ "$#" -eq 1 ] || die "1 filename argument required, $# provided"

CP="/usr/share/xroad/jlib/configuration-client.jar"


XROAD_LOG_LEVEL="INFO"

XROAD_CONFCLIENT_PARAMS=" -Xmx50m -XX:MaxMetaspaceSize=70m \
-Dxroad.appLog.xroad.level=$XROAD_LOG_LEVEL "


java ${XROAD_PARAMS} ${XROAD_CONFCLIENT_PARAMS} -Dlogback.configurationFile=/etc/xroad/conf.d/confclient-logback.xml -cp /usr/share/xroad/jlib/configuration-client.jar  ee.ria.xroad.common.conf.globalconf.ConfigurationClientMain --verifyAnchorForExternalSource $@

