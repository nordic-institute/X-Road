#!/bin/bash

# FIXME: error handling
DUMP_FILE=$1
PORT=$(crudini --get /etc/xroad/db.properties '' serverconf.hibernate.connection.url | cut -d '/' -f 3 | cut -d ':' -f2)

cat << EOC | su - postgres -c "psql -p ${PORT:-5432} postgres"
DROP DATABASE IF EXISTS serverconf_restore;
DROP DATABASE IF EXISTS serverconf_backup;
CREATE DATABASE serverconf_restore ENCODING 'UTF-8';
EOC
su - postgres -c "psql -p ${PORT:-5432} -d serverconf_restore -c \"CREATE EXTENSION hstore;\""

PW=$(crudini --get /etc/xroad/db.properties '' serverconf.hibernate.connection.password)
USER=$(crudini --get /etc/xroad/db.properties '' serverconf.hibernate.connection.username)
PGPASSWORD=${PW:-serverconf} pg_restore -h 127.0.0.1 -p ${PORT:-5432} -U ${USER:-serverconf} -O -x -n public  -1 -d serverconf_restore ${DUMP_FILE}

cat << EOC | su - postgres -c "psql -p ${PORT:-5432} postgres"
revoke connect on database serverconf from serverconf;
select pg_terminate_backend(pid) from pg_stat_activity where datname='serverconf';
ALTER DATABASE serverconf RENAME TO serverconf_backup;
ALTER DATABASE serverconf_restore RENAME TO serverconf;
grant connect on database serverconf to serverconf;
DROP DATABASE IF EXISTS serverconf_backup;
EOC

