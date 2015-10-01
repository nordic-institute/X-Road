#!/bin/sh

pkill -SIGHUP producer_proxy

# exit status 1 - No processes matched.
if [ $? -lt 2 ]; then
  exit 0
else
  exit $?
fi
    
