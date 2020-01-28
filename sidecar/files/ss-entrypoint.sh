#!/bin/bash

XROAD_SCRIPT_LOCATION=/usr/share/xroad/scripts

# Set xroad-autologin software token PIN code
if [  -n "$XROAD_TOKEN_PIN" ]
then
    echo "XROAD_TOKEN_PIN variable set, writing to /etc/xroad/autologin"
    su xroad -c 'echo $XROAD_TOKEN_PIN >/etc/xroad/autologin'
    unset XROAD_TOKEN_PIN
fi

# Generate internal and admin UI TLS certificates on the first run
if [ ! -f /etc/xroad/ssl/internal.crt ];
then
    echo "Generating new internal TLS key and certificate"
    ARGS="-n internal -f -S -p"
    $XROAD_SCRIPT_LOCATION/generate_certificate.sh $ARGS
fi

if [ ! -f /etc/xroad/ssl/nginx.crt ];
then
    echo "Generating new SSL key and certificate for the admin UI"
    ARGS="-n nginx -f -S -p"
    $XROAD_SCRIPT_LOCATION/generate_certificate.sh $ARGS
fi

# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
