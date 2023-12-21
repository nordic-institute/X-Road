#!/bin/bash

file="/etc/xroad/autologin"
if [ -n "$XROAD_TOKEN_PIN" ]
then
    echo "${XROAD_TOKEN_PIN}"
    exit 0
elif [ -f "$file" ]
then
    >&2 echo "XROAD_TOKEN_PIN variable is not set, returning PIN code at $file"
    cat $file
    exit 0
else
    >&2 echo "PIN code not available at $file"
    exit 127
fi
