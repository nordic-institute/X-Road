#!/bin/bash

ORIGINAL_FILE=/etc/xroad/db.properties
TEMP_FILE=/etc/xroad/db.properties.tmp

URL=$(crudini --get $ORIGINAL_FILE '' spring.datasource.url)

if [ -n "$URL" ]; then
  echo "Spring Datasource compatible properties already present in file. Skipping migration..."
  exit
fi

echo "Migrating to Spring Datasource properties..."

HOST=$(crudini --get $ORIGINAL_FILE '' host)
PORT=$(crudini --get $ORIGINAL_FILE '' port)
SECONDARY_HOSTS=$(crudini --get $ORIGINAL_FILE '' secondary_hosts)
USER=$(crudini --get $ORIGINAL_FILE '' username)
SCHEMA=$(crudini --get $ORIGINAL_FILE '' schema)
PASSWORD=$(crudini --get $ORIGINAL_FILE '' password)
DATABASE=$(crudini --get $ORIGINAL_FILE '' database)
SKIP_MIGRATIONS=$(crudini --get $ORIGINAL_FILE '' skip_migrations)



build_secondary_hosts() {
      local joined=""
      IFS=';' read -ra HOSTS <<< "$SECONDARY_HOSTS"
      for h in "${HOSTS[@]}"; do
        if [[ "$h" == *":"* ]]; then
          joined+="$h"
        else
          joined+="$h:$PORT"
        fi
      done
      echo "$joined"
}

if [ -z "$SCHEMA" ]; then
  SCHEMA=$USER
fi

if [[ -z "$USER" || -z "$SCHEMA" || -z "$HOST" || -z "$DATABASE" || -z "$PASSWORD" ]]; then
  die "Running config migrations failed, missing some required parameters from ${ORIGINAL_FILE}"
fi

URL="$HOST"
if [ -n "$PORT" ]; then
    URL+=":$PORT"
fi
SEC_HOSTS=$(build_secondary_hosts)
if [ -n "$SEC_HOSTS" ]; then
    URL+=",${SEC_HOSTS}"
fi

crudini --set ${TEMP_FILE} '' "spring.datasource.username" "$USER"
crudini --set ${TEMP_FILE} '' "spring.datasource.password" "$PASSWORD"
crudini --set ${TEMP_FILE} '' "spring.datasource.url" "jdbc:postgresql://${URL}/${DATABASE}"
crudini --set ${TEMP_FILE} '' "spring.datasource.hikari.data-source-properties.currentSchema" "${SCHEMA},public"
if [ -n "$SKIP_MIGRATIONS" ]; then
 crudini --set ${TEMP_FILE} '' "skip_migrations" "$SKIP_MIGRATIONS"
fi
mv -f "$ORIGINAL_FILE" "$ORIGINAL_FILE.old"
mv -f "$TEMP_FILE" "$ORIGINAL_FILE"
echo "Migration completed."
