#!/bin/bash

TMP=`mktemp -d`
DUMP_FILE=$1
HOST=$(crudini --get /etc/xroad/db.properties '' host)
PORT=$(crudini --get /etc/xroad/db.properties '' port)

cd $TMP
sed   '/-- Data/q' ${DUMP_FILE} > head
sed -e '1,/-- Data/d' ${DUMP_FILE}  > body
sed -i -e "{s/\(^COPY \(.*\) (.*$\)/DELETE FROM \2 ;\n\1/}" body
grep "DISABLE TRIGGER ALL" body > head2
grep "DELETE FROM" body > head3
grep "ENABLE TRIGGER ALL" body > tail
sed -i -e "{s/^\(.* TRIGGER ALL;\|DELETE FROM .*\)$/-- \1/}" body
cat head head2 head3 body tail > restore

if  [[ -f /etc/xroad.properties && `crudini --get /etc/xroad.properties '' postgres.connection.password` != "" ]]
then

MASTER_PW=$(crudini --get /etc/xroad.properties '' postgres.connection.password)
export PGPASSWORD=${MASTER_PW}
echo "revoke connect on database centerui_production from centerui;" | psql -h ${HOST:-localhost} -p ${PORT:-5432} -U postgres postgres
echo "select pg_terminate_backend(pid) from pg_stat_activity where datname='centerui_production' and usename='centerui';" | psql -h ${HOST:-localhost} -p ${PORT:-5432} -U postgres postgres
psql -h ${HOST:-localhost} -p ${PORT:-5432} -U postgres -e -1 centerui_production < restore
RET=$?
echo "SELECT public.fix_sequence();" | psql -h ${HOST:-localhost} -p ${PORT:-5432} -U postgres centerui_production
echo "grant connect on database centerui_production to centerui;" | psql -h ${HOST:-localhost} -p ${PORT:-5432} -U postgres postgres

else

cat << EOC | su - postgres -c "psql postgres"
revoke connect on database centerui_production from centerui;
select pg_terminate_backend(pid) from pg_stat_activity where datname='centerui_production' and usename='centerui';
EOC
sudo -i -u postgres -- psql -e -1 centerui_production < restore
RET=$?
cat << EOC | su - postgres -c "psql centerui_production"
SELECT public.fix_sequence();
EOC
cat << EOC | su - postgres -c "psql postgres"
grant connect on database centerui_production to centerui;
EOC

fi

cd /tmp
rm -rf $TMP

exit $RET
