#!/bin/bash

PW=$(crudini --get /etc/xroad/db.properties '' password)
USER=$(crudini --get /etc/xroad/db.properties '' username)

export PGPASSWORD=${PW}
BDR=$(psql -t -A -F / -h localhost -d centerui_production -U ${USER:-centerui} -c "select 1 from pg_extension WHERE extname='bdr';")
if [[ ! -z "${BDR}" ]] ; then
    echo $(psql -t -A -F / -h localhost -d centerui_production -U centerui -c "select bdr.bdr_get_local_node_name();")
fi
