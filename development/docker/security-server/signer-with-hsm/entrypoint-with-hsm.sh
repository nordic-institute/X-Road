#!/bin/bash

mkdir -p /var/lib/softhsm/tokens/

if ! softhsm2-util --show-slots | grep -q "x-road-softhsm2"; then
  softhsm2-util --init-token --slot 0 --label 'x-road-softhsm2' --so-pin 1234 --pin 'Secret1234'
fi

if ! grep -q "softhsm2:" /etc/xroad/signer-devices.yaml 2>/dev/null; then
  slot_id=$(softhsm2-util --show-slots | awk '
    /^Slot / { slot=$2 }
    /Label: *x-road-softhsm2/ { print slot }')
  cat <<EOF > /etc/xroad/signer-devices.yaml
xroad:
  signer:
    modules:
      softhsm2:
        library: /usr/lib/softhsm/libsofthsm2.so
        slot-ids: $slot_id
        os-locking-ok: true
        library-cant-create-os-threads: true
EOF
fi

chown -R xroad /var/lib/softhsm/tokens

export JAVA_OPTS="${JAVA_OPTS:+$JAVA_OPTS }-Dquarkus.config.locations=/etc/xroad/signer-devices.yaml"

exec /bin/sh /opt/app/entrypoint.sh
