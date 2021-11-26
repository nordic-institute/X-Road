#!/bin/bash

. /etc/xroad/services/global.conf

die () {
    echo >&2 "$@"
    exit 1
}


[ "$#" -eq 3 ] || [ "$#" -eq 2 ] || die "trust anchor filename, configuration path and version required, $# provided"

CP="/usr/share/xroad/jlib/configuration-client.jar"

XROAD_CONFCLIENT_PARAMS=" -Xmx50m -Dlogback.configurationFile=/etc/xroad/conf.d/confclient-logback.xml "

java ${XROAD_PARAMS} ${XROAD_CONFCLIENT_PARAMS} -cp ${CP} ee.ria.xroad.common.conf.globalconf.ConfigurationClientMain $@

