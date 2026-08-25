#!/bin/bash

mkdir -p /var/lib/softhsm/tokens/

if ! softhsm2-util --show-slots | grep -q "x-road-softhsm2"; then
  softhsm2-util --init-token --slot 0 --label 'x-road-softhsm2' --so-pin 1234 --pin 'Secret1234'
fi

chown -R xroad /var/lib/softhsm/tokens

# The module config lives in the configuration_properties DB override layer (the DSL ignores
# env/yaml). The slot id is assigned randomly at token init, so it must be computed here and
# upserted before signer starts; slot-ids keeps the uninitialized free slot from surfacing
# as a second hardware token.
slot_id=$(softhsm2-util --show-slots | awk '
  /^Slot / { slot=$2 }
  /Label: *x-road-softhsm2/ { print slot }')
# DB coordinates come from the same env the k8s chart injects into the signer pod
# (DB_CONFIG_SOURCE_*); the fallbacks are the compose dev stack's fixed values, so
# both substrates resolve without per-image wiring.
db_user="${DB_CONFIG_SOURCE_USERNAME:-serverconf}"
db_password="${DB_CONFIG_SOURCE_PASSWORD:-secret}"
until pg_isready -q -h db-serverconf -U "$db_user"; do sleep 1; done
PGPASSWORD="$db_password" psql -q -h db-serverconf -U "$db_user" -d serverconf -v ON_ERROR_STOP=1 <<EOF
DELETE FROM configuration_properties WHERE property_key = 'xroad.signer.modules';
INSERT INTO configuration_properties (property_key, property_value, created_at, updated_at) VALUES
('xroad.signer.modules',
 '{"softhsm2":{"library":"/usr/lib/softhsm/libsofthsm2.so","slot-ids":[$slot_id],"os-locking-ok":true,"library-cant-create-os-threads":true}}', now(), now());
EOF

# This should be consolidated with the entrypoint in the base image
exec java \
    -Xdebug -agentlib:jdwp=transport=dt_socket,address=*:9999,server=y,suspend=n \
    -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
    -Dquarkus.profile=containerized \
    -Djava.library.path=/usr/share/xroad/lib \
    -jar /opt/app/quarkus-run.jar
