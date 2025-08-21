#!/bin/bash

if ! grep -q "\[softhsm2\]" /etc/xroad/devices.ini 2>/dev/null; then
  printf "\n[softhsm2]\n\
    library = /usr/lib/softhsm/libsofthsm2.so\n\
    os_locking_ok = true\n\
    library_cant_create_os_threads = true\n" >> /etc/xroad/devices.ini
fi

mkdir -p /var/lib/softhsm/tokens/

if ! softhsm2-util --show-slots | grep -q "x-road-softhsm2"; then
  softhsm2-util --init-token --slot 0 --label 'x-road-softhsm2' --so-pin 1234 --pin 'Secret1234'
fi

chown -R xroad /var/lib/softhsm/tokens

source /root/entrypoint.sh