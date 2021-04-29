#!/bin/bash

PW=$(crudini --get /etc/xroad/db.properties '' serverconf.hibernate.connection.password)
USER=$(crudini --get /etc/xroad/db.properties '' serverconf.hibernate.connection.username)
HOST=$(crudini --get /etc/xroad/db.properties '' serverconf.hibernate.connection.url | cut -d '/' -f 3 | cut -d ':' -f1)
PORT=$(crudini --get /etc/xroad/db.properties '' serverconf.hibernate.connection.url | cut -d '/' -f 3 | cut -d ':' -f2)

export PGPASSWORD=${PW}
psql -t -A -F / -h "${HOST:-localhost}" -p "${PORT:-5432}" -d serverconf -U "${USER:-serverconf}" -c \
"select identifier.xroadinstance, identifier.memberclass, identifier.membercode, serverconf.servercode\
 from serverconf\
 inner join client on serverconf.owner=client.id\
 inner join identifier on client.identifier=identifier.id\
 where identifier.xroadinstance IS NOT NULL AND identifier.memberclass IS NOT NULL AND\
 identifier.membercode IS NOT NULL AND serverconf.servercode IS NOT NULL;"
