#!/bin/bash

file="/etc/xroad/autologin"

declare -a token_ids
declare -a token_pins
count=0

for var in $(compgen -e | grep '^XROAD_TOKEN_.\+_PIN$' | sort -V); do
    token_id="${var#XROAD_TOKEN_}"
    token_id="${token_id%_PIN}"
    pin_value="${!var}"

    if [ -n "$pin_value" ]; then
        token_ids+=("$token_id")
        token_pins+=("$pin_value")
        ((count++))
    fi
done

if [ -n "$XROAD_TOKEN_PIN" ]; then
    echo "${XROAD_TOKEN_PIN}"
    exit 0
elif [ "$count" -eq 1 ] && [ "${token_ids[0]}" = "0" ]; then
    echo "${token_pins[0]}"
    exit 0
elif [ "$count" -eq 1 ] && [ "${token_ids[0]}" != "0" ]; then
    >&2 echo "ERROR: Found XROAD_TOKEN_${token_ids[0]}_PIN but no other token PINs. Multiple token PINs are expected when using numbered tokens (other than 0)."
    exit 127
elif [ "$count" -gt 1 ]; then
    for i in "${!token_ids[@]}"; do
        echo "${token_ids[$i]}:${token_pins[$i]}"
    done
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
