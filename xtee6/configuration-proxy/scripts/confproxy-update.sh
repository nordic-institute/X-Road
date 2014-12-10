#!/bin/bash


die () {
    echo >&2 "$@"
    exit 1
}


if [ "$(id -nu )" != "sdsb" ] 
then
 die "ABORTED. This script must run under sdsb user "
fi



. /etc/sdsb/services/confproxy.conf

(
flock -n 200 || die "there is update process running"

umask 0002
${JAVA_HOME}/bin/java ${SDSB_PARAMS} ${CONFPROXY_PARAMS} -cp ${CP} ee.cyber.sdsb.confproxy.ConfProxyMain $@

) 200>/var/lock/sdsb.confproxy.lock
