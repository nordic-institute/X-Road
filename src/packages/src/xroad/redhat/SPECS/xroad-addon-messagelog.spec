# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:       xroad-addon-messagelog
Version:    %{xroad_version}
Release:    %{rel}%{?snapshot}%{?dist}
Summary:    X-Road AddOn: messagelog
Group:      Applications/Internet
License:    MIT
Requires:   xroad-proxy = %version-%release

%define src %{_topdir}/..

%description
AddOn for secure message log

%prep

%build

%install
mkdir -p %{buildroot}/usr/share/xroad/jlib/addon/proxy/
mkdir -p %{buildroot}/usr/share/xroad/scripts
mkdir -p %{buildroot}/etc/xroad/conf.d/addons
mkdir -p %{buildroot}/usr/share/xroad/db/messagelog
mkdir -p %{buildroot}/usr/share/doc/xroad-addon-messagelog/archive-server
mkdir -p %{buildroot}/usr/share/doc/xroad-addon-messagelog/archive-hashchain-verifier
mkdir -p %{buildroot}/usr/share/doc/%{name}

cp -p %{srcdir}/common/addon/proxy/messagelog.conf %{buildroot}/usr/share/xroad/jlib/addon/proxy/
cp -p %{srcdir}/../../../addons/messagelog/build/libs/messagelog-1.0.jar %{buildroot}/usr/share/xroad/jlib/addon/proxy/
cp -p %{srcdir}/../../../addons/messagelog/scripts/archive-http-transporter.sh %{buildroot}/usr/share/xroad/scripts
cp -p %{srcdir}/default-configuration/addons/message-log.ini %{buildroot}/etc/xroad/conf.d/addons/
cp -p %{srcdir}/common/addon/proxy/messagelog-changelog.xml %{buildroot}/usr/share/xroad/db/
cp -p %{srcdir}/common/addon/proxy/messagelog/* %{buildroot}/usr/share/xroad/db/messagelog

cp -p %{srcdir}/../../../addons/messagelog/scripts/demo-upload.pl %{buildroot}/usr/share/doc/xroad-addon-messagelog/archive-server/
cp -p %{srcdir}/../../../../doc/archive-hashchain-verifier.rb %{buildroot}/usr/share/doc/xroad-addon-messagelog/archive-hashchain-verifier/
cp -p %{srcdir}/../../../../doc/archive-hashchain-verifier.README %{buildroot}/usr/share/doc/xroad-addon-messagelog/archive-hashchain-verifier/README
cp -p %{srcdir}/../../../asicverifier/build/libs/asicverifier-1.0.jar %{buildroot}/usr/share/xroad/jlib/
ln -s /usr/share/xroad/jlib/asicverifier-1.0.jar %{buildroot}/usr/share/xroad/jlib/asicverifier.jar
cp -p %{srcdir}/../../../LICENSE.txt %{buildroot}/usr/share/doc/%{name}/
cp -p %{srcdir}/../../../securityserver-LICENSE.info %{buildroot}/usr/share/doc/%{name}/
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/%{name}/

%clean
rm -rf %{buildroot}

%files
%defattr(-,xroad,xroad,-)
%config /etc/xroad/conf.d/addons/message-log.ini
%defattr(-,root,root,-)
/usr/share/doc/xroad-addon-messagelog/archive-hashchain-verifier/README
/usr/share/doc/xroad-addon-messagelog/archive-hashchain-verifier/archive-hashchain-verifier.rb
/usr/share/doc/xroad-addon-messagelog/archive-server/demo-upload.pl
/usr/share/xroad/db/messagelog-changelog.xml
/usr/share/xroad/db/messagelog
/usr/share/xroad/jlib/addon/proxy/messagelog-1.0.jar
/usr/share/xroad/jlib/addon/proxy/messagelog.conf
/usr/share/xroad/jlib/asicverifier-1.0.jar
/usr/share/xroad/scripts/archive-http-transporter.sh
/usr/share/xroad/jlib/asicverifier.jar
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/securityserver-LICENSE.info
%doc /usr/share/doc/%{name}/CHANGELOG.md

%post

db_name=messagelog
db_url=jdbc:postgresql://127.0.0.1:5432/${db_name}
db_user=messagelog
db_passwd=$(head -c 24 /dev/urandom | base64 | tr "/+" "_-")
db_properties=/etc/xroad/db.properties

die () {
    echo >&2 "$@"
    exit 1
}

if  [[ -f ${db_properties}  && `crudini --get ${db_properties} '' messagelog.hibernate.connection.url` != "" ]]
then
    db_url=`crudini --get ${db_properties} '' messagelog.hibernate.connection.url`
    db_user=`crudini --get ${db_properties} '' messagelog.hibernate.connection.username`
    db_passwd=`crudini --get ${db_properties} '' messagelog.hibernate.connection.password`
else
    echo "no db settings detected, creating local db"

    if ! netstat -na | grep -q :5432
    then
        echo  "Is postgres running on port 5432 ?"
        echo "Aborting installation! please fix issues and rerun"
        exit 100
    fi

    if ! su - postgres -c "psql --list -tAF ' '" | grep template1 | awk '{print $3}' | grep -q "UTF8"
    then
        echo "postgreSQL is not UTF8 compatible."
        echo "Aborting installation! please fix issues and rerun"
        exit 101
    fi

    if [[ `su - postgres -c "psql postgres -tAc \"SELECT 1 FROM pg_roles WHERE rolname='$db_user'\" "` == "1" ]]
    then
        echo  "$db user exists, skipping schema creation"
        echo "ALTER ROLE ${db_user} WITH PASSWORD '${db_passwd}';" | su - postgres -c psql postgres
    else
        echo "CREATE ROLE $db_user LOGIN PASSWORD '$db_passwd';" | su - postgres -c psql postgres
    fi

    if [[ `su - postgres -c "psql postgres -tAc \"SELECT 1 FROM pg_database WHERE datname='$db_name'\""`  == "1" ]]
    then
        echo "database $db_name exists"
    else
        su - postgres -c "createdb $db_name -O $db_user -E UTF-8"
    fi

    touch ${db_properties}

    crudini --set ${db_properties} '' messagelog.hibernate.jdbc.use_streams_for_binary true
    crudini --set ${db_properties} '' messagelog.hibernate.dialect ee.ria.xroad.common.db.CustomPostgreSQLDialect
    crudini --set ${db_properties} '' messagelog.hibernate.connection.driver_class org.postgresql.Driver
    crudini --set ${db_properties} '' messagelog.hibernate.connection.url ${db_url}
    crudini --set ${db_properties} '' messagelog.hibernate.connection.username  ${db_user}
    crudini --set ${db_properties} '' messagelog.hibernate.connection.password ${db_passwd}
fi

chown xroad:xroad ${db_properties}
chmod 640 ${db_properties}

echo "running ${db_name} database migrations"
cd /usr/share/xroad/db/
/usr/share/xroad/db/liquibase.sh --classpath=/usr/share/xroad/jlib/proxy.jar --url="${db_url}?dialect=ee.ria.xroad.common.db.CustomPostgreSQLDialect" --changeLogFile=/usr/share/xroad/db/${db_name}-changelog.xml --password=${db_passwd} --username=${db_user}  update || die "Connection to database has failed, please check database availability and configuration ad ${db_properties} file"

%changelog

