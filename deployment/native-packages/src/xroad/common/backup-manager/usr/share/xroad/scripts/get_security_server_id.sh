#!/bin/bash
if [ -r /etc/xroad/db.properties ]; then
  source /usr/share/xroad/scripts/read_db_properties.sh
  read_serverconf_database_properties /etc/xroad/db.properties

  # Reading custom libpq ENV variables
  if [ -f /etc/xroad/db_libpq.env ]; then
    source /etc/xroad/db_libpq.env
  fi

  PGPASSWORD="$db_password" \
  psql -q -t -A -F / -h "${PGHOST:-$db_addr}" -p "${PGPORT:-$db_port}" -d "${db_database}" -U "${db_user}" 2>/dev/null <<EOF
select id.xroadinstance, id.memberclass, id.membercode, s.servercode
from "${db_schema}".serverconf s
join "${db_schema}".client c on s.owner=c.id
join "${db_schema}".identifier id on c.identifier=id.id
where id.xroadinstance IS NOT NULL AND id.memberclass IS NOT NULL AND
id.membercode IS NOT NULL AND s.servercode IS NOT NULL;
EOF
else
  exit 1
fi
