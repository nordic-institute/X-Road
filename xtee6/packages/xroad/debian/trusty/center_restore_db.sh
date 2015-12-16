#!/bin/sh

TMP=`mktemp -d`

# FIXME: error handling
DUMP_FILE=$1

cd $TMP
sed   '/-- Data/q' ${DUMP_FILE} > head
sed -e '1,/-- Data/d' ${DUMP_FILE}  > body
sed -i -e "{s/\(^COPY \(.*\) (.*$\)/DELETE FROM \2 ;\n\1/}" body
grep "DISABLE TRIGGER ALL" body > head2
grep "DELETE FROM" body > head3
grep "ENABLE TRIGGER ALL" body > tail
sed -i -e "{s/^\(.* TRIGGER ALL;\|DELETE FROM .*\)$/-- \1/}" body
cat head head2 head3 body tail > restore
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

cd /tmp
rm -rf $TMP

exit $RET
