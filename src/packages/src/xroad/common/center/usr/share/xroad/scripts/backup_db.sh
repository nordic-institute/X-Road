#!/bin/bash

set -x

TMP=$(mktemp)
DUMP_FILE=$1
HOST=$(crudini --get /etc/xroad/db.properties '' host)
PORT=$(crudini --get /etc/xroad/db.properties '' port)
USER=$(crudini --get /etc/xroad/db.properties '' username)
SCHEMA=$(crudini --get /etc/xroad/db.properties '' schema)
PASSWORD=$(crudini --get /etc/xroad/db.properties '' password)
DATABASE=$(crudini --get /etc/xroad/db.properties '' database)

PGOPTIONS='-c client-min-messages=warning' PGPASSWORD="$PASSWORD" \
    pg_dump -n "${SCHEMA:-$USER}" -x -O -F p -h "${HOST:-127.0.0.1}" -p "${PORT:-5432}" -U "${USER:-centerui}" -f "${DUMP_FILE}" \
    "${DATABASE:-centerui_production}" 1>"$TMP" 2>&1
RET=$?

if [[ $RET -ne 0 ]]; then
    cat "$TMP"
fi
rm -f "$TMP"

exit $RET
