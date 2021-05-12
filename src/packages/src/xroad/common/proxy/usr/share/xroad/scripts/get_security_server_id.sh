#!/bin/bash

source /usr/share/xroad/scripts/read_db_properties.sh
read_serverconf_database_properties /etc/xroad/db.properties

PGPASSWORD="$db_password" \
psql -t -A -F / -h "${db_addr}" -p "${db_port}" -d "${db_database}" -U "${db_user}" <<EOF
select id.xroadinstance, id.memberclass, id.membercode, s.servercode
from "${db_schema}".serverconf s
join "${db_schema}".client c on s.owner=c.id
join "${db_schema}".identifier id on c.identifier=id.id
where id.xroadinstance IS NOT NULL AND id.memberclass IS NOT NULL AND
id.membercode IS NOT NULL AND s.servercode IS NOT NULL;
EOF
