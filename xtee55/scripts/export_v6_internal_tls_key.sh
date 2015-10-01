#!/bin/sh

if [ "$(id -nu )" != "ui" ]
then
  echo "ABORTED. This script must run under ui user" >&2
  exit 1
fi

XCONF_ROOT=${XTEETOP:-/usr/xtee}/etc/xtee

INTKEY=$XCONF_ROOT/proxy/intkey
INTCERT=$XCONF_ROOT/proxy/intcert

SOURCE=/etc/xroad/ssl/internal

do_reload=1

for var in "$@"
do
    case "$var"
    in
        "-no-reload")
            do_reload=0
            ;;
    esac
done


if [ -e $SOURCE.key -a -e $SOURCE.crt ]; then
    # convert internal.key to intkey (der)
    openssl pkcs8 -inform pem -in $SOURCE.key -nocrypt -outform der \
            -out $INTKEY -topk8 || exit $?

    # convert internal.crt to intcert (der)
    openssl x509 -inform pem -in $SOURCE.crt -outform der -out $INTCERT || exit $?

    # reload v5 producer proxy
    if [ $do_reload -eq 1 ]; then
      /usr/share/xroad/scripts/reload_producer_proxy.sh || exit $?
    fi
fi

