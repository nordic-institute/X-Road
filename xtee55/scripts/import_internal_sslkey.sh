#!/bin/sh

XCONF_ROOT=${XTEETOP:-/usr/xtee}/etc/xtee

INTKEY=$XCONF_ROOT/proxy/intkey
INTCERT=$XCONF_ROOT/proxy/intcert

DEST=/etc/xroad/ssl/internal

do_import=1
do_export=0
do_reload=1

for var in "$@"
do
    case "$var"
    in
        "-import")
            do_import=1
            do_export=0
            ;;
        "-export")
            do_export=1
            do_import=0
            ;;
        "-no-reload")
            do_reload=0
            ;;
    esac
done

if [ $do_import -eq 1 ]; then
    if [ -e $INTCERT -a -e $INTKEY ]; then
        # convert intkey (der) to internal.key (pem)
        openssl pkcs8 -inform der -in $INTKEY -nocrypt \
                -outform pem -out ${DEST}.key || exit 1

        # convert intcert (der) to internal.crt (pem)
        openssl x509 -inform der -in $INTCERT \
                -outform pem -out $DEST.crt || exit 1 

        # convert internal.crt and internal.key to internal.p12
        openssl pkcs12 -export -in $DEST.crt -inkey ${DEST}.key \
                -name "internal" -out $DEST.p12 \
                -passout pass:internal || exit 1
        chown xroad:xroad ${DEST}.key $DEST.crt $DEST.p12 || exit 1
    fi

    if [ $do_reload -eq 1 ]; then
        /etc/init.d/nginx reload
        restart xtee55-servicemediator || start xtee55-servicemediator
    fi
elif [ $do_export -eq 1 ] && [ -e $DEST.key -a -e $DEST.crt ]; then
    # convert internal.key to intkey (der)
    openssl pkcs8 -inform pem -in $DEST.key -nocrypt -outform der \
            -out $INTKEY -topk8 || exit 1

    # convert internal.crt to intcert (der)
    openssl x509 -inform pem -in $DEST.crt -outform der -out $INTCERT || exit 1

    if [ $do_reload -eq 1 ]; then
        pkill -SIGHUP producer_proxy
        # exit status 1 - No processes matched.

        if [ $? -lt 2 ]; then
            exit 0
        else
            exit $?
        fi
    fi
fi
