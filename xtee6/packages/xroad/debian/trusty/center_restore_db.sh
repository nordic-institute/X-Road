#!/bin/sh

TMP=`mktemp -d`

cd $TMP
sed   '/-- Data/q' /var/lib/xroad/dbdump.dat > head
sed -e '1,/-- Data/d' /var/lib/xroad/dbdump.dat  > body
sed -i -e "{s/\(^COPY \(.*\) (.*$\)/truncate table \2 cascade;\n\1/}" body
grep "DISABLE TRIGGER ALL" body > head2
grep "truncate table" body > head3
grep "ENABLE TRIGGER ALL" body > tail
sed -i -e "{s/^\(.* TRIGGER ALL;\|truncate table .*\)$/-- \1/}" body
cat head head2 head3 body tail > restore
cat << EOC | su - postgres -c "psql postgres"
revoke connect on database centerui_production from centerui;
select pg_terminate_backend(pid) from pg_stat_activity where datname='centerui_production';
EOC
sudo -i -u postgres -- psql -e -1 centerui_production < restore
RET=$?
cat << EOC | su - postgres -c "psql postgres"
grant connect on database centerui_production to centerui;
EOC

cd /tmp
rm -rf $TMP

exit $RET
