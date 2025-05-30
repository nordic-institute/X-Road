#!/bin/bash

db_addr="${XROAD_SERVERCONF_DB_HOST:-db-serverconf}"
db_port="${XROAD_SERVERCONF_DB_PORT:-5432}"
db_database="${XROAD_SERVERCONF_DB_DATABASE:-serverconf}"
db_schema="${XROAD_SERVERCONF_DB_SCHEMA:-public}"
db_user="${XROAD_SERVERCONF_DB_USER:-serverconf}"
db_password="${XROAD_SERVERCONF_DB_PASSWORD}"


SECURITY_SERVER_ID=$(PGPASSWORD="$db_password" \
  psql -q -t -A -F / -h "${PGHOST:-$db_addr}" -p "${PGPORT:-$db_port}" -d "${db_database}" -U "${db_user}" 2>/dev/null <<EOF
select id.xroadinstance, id.memberclass, id.membercode, s.servercode
from "${db_schema}".serverconf s
join "${db_schema}".client c on s.owner=c.id
join "${db_schema}".identifier id on c.identifier=id.id
where id.xroadinstance IS NOT NULL AND id.memberclass IS NOT NULL AND
id.membercode IS NOT NULL AND s.servercode IS NOT NULL;
EOF
)

if [[ -n "${SECURITY_SERVER_ID}" ]] ; then
  SCRIPT="/usr/share/xroad/scripts/containerised/create_backup.sh"
  FILENAME="/var/lib/xroad/backup/ss-automatic-backup-$(date +%Y_%m_%d_%H%M%S).gpg"
  ${SCRIPT} -s "${SECURITY_SERVER_ID}" -f "${FILENAME}"
fi

