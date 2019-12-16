#!/bin/bash

# Set autologin token PIN code
if [  -n "$XROAD_TOKEN_PIN" ]
then
    echo "XROAD_TOKEN_PIN variable set, writing to /etc/xroad/autologin"
    echo "$XROAD_TOKEN_PIN" > /etc/xroad/autologin
    unset XROAD_TOKEN_PIN
fi

# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
