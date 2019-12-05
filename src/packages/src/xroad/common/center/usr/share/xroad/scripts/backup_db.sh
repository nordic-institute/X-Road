#!/bin/bash

TMP=`mktemp`
DUMP_FILE=$1
HOST=$(crudini --get /etc/xroad/db.properties '' host)
PORT=$(crudini --get /etc/xroad/db.properties '' port)

PGPASSWORD=centerui pg_dump -a -n public --disable-triggers -T schema_migrations -F p -h ${HOST:-127.0.0.1} -p ${PORT:-5432} -U centerui -f ${DUMP_FILE} centerui_production 1>$TMP 2>&1

RET=$?
if [[ $RET -ne 0 ]]
then
cat $TMP
fi
rm -f $TMP

exit $RET
