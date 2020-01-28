#!/bin/bash

# Set xroad-autologin software token PIN code
if [  -n "$XROAD_TOKEN_PIN" ]
then
    echo "XROAD_TOKEN_PIN variable set, writing to /etc/xroad/autologin"
    su xroad -c 'echo $XROAD_TOKEN_PIN >/etc/xroad/autologin'
    unset XROAD_TOKEN_PIN
fi

# Generate internal TLS certificates on the first run
if [ -f $FILE ];
then
    echo "Generating new webUI TLS key/certificate with $subj and $altn"
    XROAD_SCRIPT_LOCATION=/usr/share/xroad/scripts
    NAME=internal
    ARGS="-n "$NAME" -f -S -p"
    set -- $ARGS
    $XROAD_SCRIPT_LOCATION/generate_certificate.sh $ARGS

    NAME=nginx
    ARGS="-n "$NAME" -f -S -p"
    set -- $ARGS
    $XROAD_SCRIPT_LOCATION/generate_certificate.sh $ARGS
fi

# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
