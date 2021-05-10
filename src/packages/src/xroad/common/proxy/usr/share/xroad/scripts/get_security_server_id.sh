#!/bin/bash

source /usr/share/xroad/scripts/read_db_properties.sh
read_serverconf_database_properties /etc/xroad/db.properties

PGPASSWORD="$db_password" psql -t -A -F / -h "${db_addr}" -p "${db_port}" -d ${db_schema} -U "${db_user}" -c \
"select identifier.xroadinstance, identifier.memberclass, identifier.membercode, serverconf.servercode\
 from serverconf\
 inner join client on serverconf.owner=client.id\
 inner join identifier on client.identifier=identifier.id\
 where identifier.xroadinstance IS NOT NULL AND identifier.memberclass IS NOT NULL AND\
 identifier.membercode IS NOT NULL AND serverconf.servercode IS NOT NULL;"
