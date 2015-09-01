#!/bin/bash

. /etc/xroad/services/global.conf

die () {
    echo >&2 "$@"
    exit 1
}


[ "$#" -eq 2 ] || die "trust anchor filename and configuration path required, $# provided"

CP="/usr/share/xroad/jlib/configuration-client.jar"

CONFCLIENT_PARAMS=" -Xmx50m -Dlogback.configurationFile=/etc/xroad/conf.d/confclient-logback.xml "

${JAVA_HOME}/bin/java ${XROAD_PARAMS} ${CONFCLIENT_PARAMS} -cp ${CP} ee.ria.xroad.common.conf.globalconf.ConfigurationClientMain $@

