#!/bin/bash

NAME=internal
DIR=/etc/xroad/ssl
XROAD_SCRIPT_LOCATION=/usr/share/xroad/scripts
ARGS="-n "$NAME" -f -S -p"

# Set xroad-autologin software token PIN code
if [  -n "$XROAD_TOKEN_PIN" ]
then
    echo "XROAD_TOKEN_PIN variable set, writing to /etc/xroad/autologin"
    su xroad -c 'echo $XROAD_TOKEN_PIN >/etc/xroad/autologin'
    unset XROAD_TOKEN_PIN
fi

# Generate internal TLS certificate
set -- $ARGS
$XROAD_SCRIPT_LOCATION/generate_certificate.sh $@

# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
