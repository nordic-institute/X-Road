#!/bin/bash

. /etc/sdsb/services/global.conf

die () {
    echo >&2 "$@"
    exit 1
}


[ "$#" -eq 2 ] || die "trust anchor filename and configuration path required, $# provided"

CP="/usr/share/sdsb/jlib/configuration-client.jar"


SDSB_LOG_LEVEL="INFO"

CONFCLIENT_PARAMS=" -Xmx50m -Dee.cyber.sdsb.appLog.sdsb.level=$SDSB_LOG_LEVEL "

${JAVA_HOME}/bin/java ${SDSB_PARAMS} ${CONFCLIENT_PARAMS} -cp ${CP} ee.cyber.sdsb.common.conf.globalconf.ConfigurationClientMain $@

