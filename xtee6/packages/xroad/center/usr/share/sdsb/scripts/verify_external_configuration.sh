#!/bin/bash

. /etc/sdsb/services/global.conf

die () {
    echo >&2 "$@"
    exit 1
}


[ "$#" -eq 1 ] || die "1 filename argument required, $# provided"

CP="/usr/share/sdsb/jlib/configuration-client.jar"


SDSB_LOG_LEVEL="INFO"

CONFCLIENT_PARAMS=" -Xmx50m -XX:MaxMetaspaceSize=70m \
-Dee.cyber.sdsb.appLog.sdsb.level=$SDSB_LOG_LEVEL "


${JAVA_HOME}/bin/java ${SDSB_PARAMS} ${CONFCLIENT_PARAMS} -Dlogback.configurationFile=/etc/sdsb/conf.d/confclient-logback.xml -cp /usr/share/sdsb/jlib/configuration-client.jar  ee.cyber.sdsb.common.conf.globalconf.ConfigurationClientMain $@

