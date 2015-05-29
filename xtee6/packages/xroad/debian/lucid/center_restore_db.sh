#!/bin/sh

cat << EOC | su - postgres -c "psql postgres"
DROP DATABASE IF EXISTS centerui_restore;
DROP DATABASE IF EXISTS centerui_backup;
CREATE DATABASE centerui_restore ENCODING 'UTF-8';
EOC

PGPASSWORD=centerui pg_restore -h 127.0.0.1 -U centerui -O -x -1 -n public -d centerui_restore /var/lib/xroad/dbdump.dat


cat << EOC | su - postgres -c "psql postgres"
revoke connect on database centerui_production from centerui;
select pg_terminate_backend(procpid) from pg_stat_activity where datname='centerui_production';
ALTER DATABASE centerui_production RENAME TO centerui_backup;
ALTER DATABASE centerui_restore RENAME TO centerui_production;
grant connect on database centerui_production to centerui;
DROP DATABASE IF EXISTS centerui_backup;
EOC


