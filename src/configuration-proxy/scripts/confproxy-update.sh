#!/bin/bash


die () {
    echo >&2 "$@"
    exit 1
}


if [ "$(id -nu )" != "xroad" ]
then
 die "ABORTED. This script must run under xroad user "
fi



. /etc/xroad/services/confproxy.conf

(
flock -n 200 || die "there is update process running"

umask 0002
java -XX:UseSVE=0 ${XROAD_PARAMS} ${XROAD_CONFPROXY_PARAMS} -cp ${CP} ee.ria.xroad.confproxy.ConfProxyMain $@

) 200>/var/lock/xroad.confproxy.lock
