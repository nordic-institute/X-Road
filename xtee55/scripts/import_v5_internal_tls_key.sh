#!/bin/sh

if [ "$(id -nu )" != "root" ]
then
  echo "ABORTED. This script must run under root user" >&2
  exit 1
fi

XCONF_ROOT=${XTEETOP:-/usr/xtee}/etc/xtee

INTKEY=$XCONF_ROOT/proxy/intkey
INTCERT=$XCONF_ROOT/proxy/intcert

DEST=/etc/xroad/ssl/internal

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


if [ -e $INTCERT -a -e $INTKEY ]; then
    # convert intkey (der) to internal.key (pem)
    openssl pkcs8 -inform der -in $INTKEY -nocrypt \
            -outform pem -out ${DEST}.key || exit $?

    # convert intcert (der) to internal.crt (pem)
    openssl x509 -inform der -in $INTCERT \
            -outform pem -out $DEST.crt || exit $?

    # convert internal.crt and internal.key to internal.p12
    openssl pkcs12 -export -in $DEST.crt -inkey ${DEST}.key \
            -name "internal" -out $DEST.p12 \
            -passout pass:internal || exit $?

    chown xroad:xroad ${DEST}.key $DEST.crt $DEST.p12 || exit $?
fi

if [ $do_reload -eq 1 ]; then
    restart xtee55-clientmediator
    restart xroad-proxy
fi

