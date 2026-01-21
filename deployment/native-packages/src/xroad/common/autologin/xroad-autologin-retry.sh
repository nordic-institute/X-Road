#!/bin/bash
# allow signer to start up
sleep 3

echo "(re)trying to enter PIN"
expect -f /usr/share/xroad/autologin/autologin.expect

exit $?
