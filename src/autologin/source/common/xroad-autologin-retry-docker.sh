#!/bin/bash
# allow signer to start up
sleep 3
return_value=-1
while [ $return_value -ne 0 ]
do
    echo "(re)trying to enter PIN"
    expect -f /usr/share/xroad/autologin/autologin.expect
    return_value=$?
    if [ $return_value -eq 127 ]
        then
        echo "wrong PIN, or PIN not available and should not retry"
        break
    fi
    sleep 3
done
