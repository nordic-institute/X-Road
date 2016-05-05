#!/bin/bash

cat << EOC | su - postgres -c "psql postgres"
DROP DATABASE IF EXISTS serverconf_restore;
DROP DATABASE IF EXISTS serverconf_backup;
CREATE DATABASE serverconf_restore ENCODING 'UTF-8';
EOC
su - postgres -c "psql -d serverconf_restore -c \"CREATE EXTENSION hstore;\""

PW=$(crudini --get /etc/xroad/db.properties '' serverconf.hibernate.connection.password)
USER=$(crudini --get /etc/xroad/db.properties '' serverconf.hibernate.connection.username)
PGPASSWORD=${PW:-serverconf} pg_restore -h 127.0.0.1 -U ${USER:-serverconf} -O -x -n public  -1 -d serverconf_restore /var/lib/xroad/dbdump.dat

cat << EOC | su - postgres -c "psql postgres"
revoke connect on database serverconf from serverconf;
select pg_terminate_backend(pid) from pg_stat_activity where datname='serverconf';
ALTER DATABASE serverconf RENAME TO serverconf_backup;
ALTER DATABASE serverconf_restore RENAME TO serverconf;
grant connect on database serverconf to serverconf;
DROP DATABASE IF EXISTS serverconf_backup;
EOC

