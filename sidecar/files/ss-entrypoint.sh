#!/bin/bash

# Set globalconf expiration date to one year from now
#DATE=`date -Iseconds -u -d "+1 year"`
#sed -i "s#expirationDate\":\".*\",#expirationDate\":\"${DATE}\",#g" /etc/xroad/globalconf/CS/*-params.xml.metadata

if [  -n "$XROAD_TOKEN_PIN" ]
then
    echo "XROAD_TOKEN_PIN variable set, writing to /etc/xroad/autologin"
    echo "$XROAD_TOKEN_PIN" > /etc/xroad/autologin
    unset XROAD_TOKEN_PIN
fi

# Start services
exec /usr/bin/supervisord -n -c /etc/supervisor/supervisord.conf
