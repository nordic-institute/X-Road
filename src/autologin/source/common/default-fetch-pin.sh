#!/usr/bin/env bash
file="/etc/xroad/autologin"
if [ -f "$file" ]
then
    cat $file
    exit 0
else
    >&2 echo "PIN code not available at $file"
    exit 127
fi
