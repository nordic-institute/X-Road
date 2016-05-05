#!/bin/bash
PW=$(crudini --get /etc/xroad/db.properties '' serverconf.hibernate.connection.password)
USER=$(crudini --get /etc/xroad/db.properties '' serverconf.hibernate.connection.username)
PGPASSWORD=${PW:-serverconf} pg_dump -F t -h 127.0.0.1 -U ${USER:-serverconf} -f /var/lib/xroad/dbdump.dat serverconf

