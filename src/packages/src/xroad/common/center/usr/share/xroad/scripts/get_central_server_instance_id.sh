#!/bin/bash

FILENAME=/etc/xroad/globalconf/instance-identifier
if [[ -r "$FILENAME" ]] ; then
  cat "${FILENAME}"
fi
