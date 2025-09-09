#!/bin/bash

mkdir -p /var/lib/softhsm/tokens/

if ! softhsm2-util --show-slots | grep -q "x-road-softhsm2"; then
  softhsm2-util --init-token --slot 0 --label 'x-road-softhsm2' --so-pin 1234 --pin 'Secret1234'
fi

if ! grep -q "\[softhsm2\]" /etc/xroad/devices.ini 2>/dev/null; then
  slot_id=$(softhsm2-util --show-slots | awk '
    /^Slot / { slot=$2 }
    /Label: *x-road-softhsm2/ { print slot }')
  printf "\n[softhsm2]\n\
    library = /usr/lib/softhsm/libsofthsm2.so\n\
    slot_ids = %s\n\
    os_locking_ok = true\n\
    library_cant_create_os_threads = true\n" "$slot_id" >> /etc/xroad/devices.ini
fi

chown -R xroad /var/lib/softhsm/tokens

# This should be consolidated with the entrypoint in the base image
exec java \
    -Xdebug -agentlib:jdwp=transport=dt_socket,address=*:9999,server=y,suspend=n \
    -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
    -Dquarkus.profile=containerized \
    -Djava.library.path=/usr/share/xroad/lib \
    -jar /opt/app/quarkus-run.jar
