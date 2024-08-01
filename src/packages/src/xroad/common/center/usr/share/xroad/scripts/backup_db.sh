#!/bin/bash

source /usr/share/xroad/scripts/_read_cs_db_properties.sh

prepare_db_props

TMP=$(mktemp)
DUMP_FILE=$1
HOST=${db_host:-127.0.0.1}
PORT=${db_port:-5432}
USER=${db_user}
SCHEMA=${db_schema}
PASSWORD=${db_password}
DATABASE=${db_database}
ADMIN_USER=$(crudini --get /etc/xroad.properties '' centerui.database.admin_user)
ADMIN_PASSWORD=$(crudini --get /etc/xroad.properties '' centerui.database.admin_password)

if [[ -n "${ADMIN_USER}" && -n "${ADMIN_PASSWORD}" ]]; then
  USER=${ADMIN_USER}
  PASSWORD=${ADMIN_PASSWORD}
fi

# Reading custom libpq ENV variables
if [ -f /etc/xroad/db_libpq.env ]; then
  source /etc/xroad/db_libpq.env
fi

if [ ! -z $PGOPTIONS_EXTRA ]; then
  PGOPTIONS_EXTRA=" ${PGOPTIONS_EXTRA}"
fi

PGOPTIONS="-c client-min-messages=warning${PGOPTIONS_EXTRA-}" PGPASSWORD="$PASSWORD" \
    pg_dump -n "${SCHEMA:-$USER}" -x -O -F p -h "${PGHOST:-$HOST}" -p "${PGPORT:-$PORT}" -U "${USER:-centerui}" -f "${DUMP_FILE}" \
    "${DATABASE:-centerui_production}" 1>"$TMP" 2>&1
RET=$?

if [[ $RET -ne 0 ]]; then
    cat "$TMP"
fi
rm -f "$TMP"

exit $RET
