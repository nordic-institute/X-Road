#!/bin/sh
#
# Database setup
#

init_postgres() {
    SERVICE_NAME=postgresql

    # check if postgres is already running
    systemctl is-active $SERVICE_NAME && return 0

    # Copied from postgresql-setup. Determine default data directory
    PGDATA=`systemctl show -p Environment "${SERVICE_NAME}.service" |
    sed 's/^Environment=//' | tr ' ' '\n' |
    sed -n 's/^PGDATA=//p' | tail -n 1`
    if [ x"$PGDATA" = x ]; then
        echo "failed to find PGDATA setting in ${SERVICE_NAME}.service"
        return 1
    fi

    if ! postgresql-check-db-dir $PGDATA >/dev/null; then
        PGSETUP_INITDB_OPTIONS="--auth-host=md5 -E UTF8" postgresql-setup initdb || return 1
    fi

    # ensure that PostgreSQL is running
    systemctl start $SERVICE_NAME || return 1
}

db_name=serverconf
db_url=jdbc:postgresql://127.0.0.1:5432/${db_name}
db_user=serverconf
db_passwd=$(head -c 24 /dev/urandom | base64 | tr "/+" "_-")
db_properties=/etc/xroad/db.properties

#is database connection configured?
if  [[ -f ${db_properties}  && `crudini --get ${db_properties} '' serverconf.hibernate.connection.url` != "" ]]
then
    exit 0
else
    echo "no db settings detected, creating local db"

    init_postgres || exit 1

    if ! netstat -na | grep -q :5432
    then echo -e  "\n\nIs postgres running on port 5432 ?"
        echo -e "Aborting installation! please fix issues and rerun\n\n"
        exit 100
    fi

    if  ! su - postgres -c "psql --list -tAF ' '" | grep template1 | awk '{print $3}' | grep -q "UTF8"
    then echo -e "\n\npostgreSQL is not UTF8 compatible."
        echo -e "Aborting installation! please fix issues and rerun\n\n"
        exit 101
    fi

    if [[ `su - postgres -c "psql postgres -tAc \"SELECT 1 FROM pg_roles WHERE rolname='${db_user}'\" "` == "1" ]]
    then
        echo  "$db_user user exists, skipping schema creation"
        echo "ALTER ROLE ${db_user} WITH PASSWORD '${db_passwd}';" | su - postgres -c psql postgres
    else
        echo "CREATE ROLE ${db_user} LOGIN PASSWORD '${db_passwd}';" | su - postgres -c psql postgres
    fi

    if [[ `su - postgres -c "psql postgres -tAc \"SELECT 1 FROM pg_database WHERE datname='${db_name}'\""`  == "1" ]]
    then
        echo "database ${db_name} exists"
    else
        su - postgres -c "createdb ${db_name} -O ${db_user} -E UTF-8"
    fi

    su - postgres -c "psql serverconf -tAc \"CREATE EXTENSION IF NOT EXISTS hstore;\""

    touch ${db_properties}
    crudini --set ${db_properties} '' serverconf.hibernate.jdbc.use_streams_for_binary true
    crudini --set ${db_properties} '' serverconf.hibernate.dialect ee.ria.xroad.common.db.CustomPostgreSQLDialect
    crudini --set ${db_properties} '' serverconf.hibernate.connection.driver_class org.postgresql.Driver
    crudini --set ${db_properties} '' serverconf.hibernate.connection.url ${db_url}
    crudini --set ${db_properties} '' serverconf.hibernate.connection.username  ${db_user}
    crudini --set ${db_properties} '' serverconf.hibernate.connection.password ${db_passwd}
fi

chown xroad:xroad ${db_properties}
chmod 640 ${db_properties}

exit 0

