%include %{_specdir}/common.inc
# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:       xroad-base
Version:    %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:    %{rel}%{?snapshot}%{?dist}
Summary:    X-Road base components
Group:      Applications/Internet
License:    MIT
Requires(post): systemd
Requires(preun): systemd
Requires(postun): systemd
BuildRequires: systemd
Requires:  systemd
%if 0%{?el7}
Requires:  rlwrap
%endif
Requires:  jre-21-headless, tzdata-java
Requires:  crudini, hostname, sudo, openssl, bc, python3, python3-pyyaml

%define src %{_topdir}/..

%description
X-Road base components and utilities

%prep
rm -rf base
cp -a %{srcdir}/common/base .
cd base

%build

%install
cd base
cp -a * %{buildroot}

mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}/usr/share/xroad/jlib
mkdir -p %{buildroot}/usr/share/xroad/lib
mkdir -p %{buildroot}/etc/xroad
mkdir -p %{buildroot}/etc/xroad/services
mkdir -p %{buildroot}/etc/xroad/conf.d/addons
mkdir -p %{buildroot}/usr/share/doc/%{name}
mkdir -p %{buildroot}/etc/xroad/ssl
mkdir -p %{buildroot}/var/lib/xroad
mkdir -p %{buildroot}/etc/xroad/backup.d

ln -s /usr/share/xroad/jlib/common-db-1.0.jar %{buildroot}/usr/share/xroad/jlib/common-db.jar
ln -s /usr/share/xroad/jlib/postgresql-42.7.8.jar %{buildroot}/usr/share/xroad/jlib/postgresql.jar
ln -s /usr/share/xroad/db/liquibase-core-4.19.0.jar %{buildroot}/usr/share/xroad/db/liquibase-core.jar

cp -p %{_sourcedir}/base/xroad-base.service %{buildroot}%{_unitdir}
cp -p %{srcdir}/../../../../src/common/common-db/build/libs/common-db-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{srcdir}/../../../../src/security-server/admin-service/application/build/unpacked-libs/postgresql-42.7.8.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{srcdir}/../../../../src/LICENSE.txt %{buildroot}/usr/share/doc/%{name}/LICENSE.txt
cp -p %{srcdir}/../../../../src/3RD-PARTY-NOTICES.txt %{buildroot}/usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
cp -p %{srcdir}/common/base/usr/share/xroad/db/liquibase-core-4.19.0.jar %{buildroot}/usr/share/xroad/db/liquibase-core-4.19.0.jar
cp -p %{srcdir}/common/base/usr/share/xroad/db/liquibase.sh %{buildroot}/usr/share/xroad/db/liquibase.sh
cp -p %{srcdir}/common/helper-scripts/yaml_helper.py %{buildroot}/usr/share/xroad/scripts/yaml_helper.py
cp -p %{srcdir}/common/helper-scripts/yaml_helper.sh %{buildroot}/usr/share/xroad/scripts/yaml_helper.sh
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/%{name}/CHANGELOG.md

%clean
rm -rf %{buildroot}

%files
%defattr(0640,xroad,xroad,0751)
%attr(0440,root,root) %config /etc/sudoers.d/xroad-restore
%dir /etc/xroad
%dir /etc/xroad/ssl
%dir /etc/xroad/services
%dir /etc/xroad/conf.d
%dir /etc/xroad/conf.d/addons
%dir /var/lib/xroad
%config /etc/xroad/services/global.conf
%config /etc/xroad/ssl/openssl.cnf

%defattr(-,root,root,-)
%attr(644,root,root) %{_unitdir}/xroad-base.service
%dir /usr/share/xroad
/usr/share/xroad/jlib/common-db.jar
/usr/share/xroad/jlib/common-db-1.0.jar
/usr/share/xroad/jlib/postgresql.jar
/usr/share/xroad/jlib/postgresql-*.jar
/usr/share/xroad/scripts/_backup_xroad.sh
/usr/share/xroad/scripts/generate_certificate.sh
/usr/share/xroad/scripts/generate_gpg_keypair.sh
/usr/share/xroad/scripts/_restore_xroad.sh
/usr/share/xroad/scripts/_backup_restore_common.sh
/usr/share/xroad/scripts/serverconf_migrations/add_acl.xsl
/usr/share/xroad/scripts/_setup_db.sh
/usr/share/xroad/scripts/_setup_memory.sh
%attr(755,root,root) /usr/share/xroad/scripts/yaml_helper.py
%attr(755,root,root) /usr/share/xroad/scripts/yaml_helper.sh
/usr/share/xroad/db/liquibase-core.jar
/usr/share/xroad/db/liquibase-core-*.jar
/usr/share/xroad/db/liquibase.sh
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
%doc /usr/share/doc/%{name}/CHANGELOG.md

%pre -p /bin/bash
%upgrade_check

if ! getent passwd xroad > /dev/null; then
useradd --system --home /var/lib/xroad --no-create-home --shell /bin/bash --user-group --comment "X-Road system user" xroad
fi


%define set_default_java_version()                                                                                         \
  if [ $1 -ge 1 ] ; then                                                                                                \
    `# 7.4.0. Check that the default java version is at least 21`                                                       \
    java_version_supported() {                                                                                          \
      local java_exec=$1                                                                                                \
      local java_version=$("$java_exec" -version 2>&1 | grep -i version | cut -d '"' -f2 | cut -d. -f1)                 \
      [[ $java_version -ge 21 ]]                                                                                        \
    }                                                                                                                   \
    if ! java_version_supported /etc/alternatives/java; then                                                            \
      if [ -x /etc/alternatives/jre_21/bin/java ] && java_version_supported /etc/alternatives/jre_21/bin/java; then     \
        echo "Configuring Java 21 as the default version..."                                                            \
        alternatives --set java $(readlink -f /etc/alternatives/jre_21)/bin/java                                        \
      else                                                                                                              \
        echo "Cannot find supported java version. Please set system default java installation with 'alternatives' command." >&2   \
      fi                                                                                                                 \
    fi                                                                                                                   \
                                                                                                                         \
  fi

%define restart_xroad_services()                                                                                                                                 \
  services_to_restart=$(find %{_localstatedir}/lib/rpm-state -type f -name "active" -exec dirname {} \\; | xargs -I {} basename {} | grep xroad- | tr '\\n' ' ') \
  if [ -n "$services_to_restart" ]; then                                                                                                                         \
    echo "Restarting services: $services_to_restart"                                                                                                             \
    for service_name in $services_to_restart; do                                                                                                                 \
      systemctl reset-failed "$service_name" > /dev/null 2>&1 || :                                                                                               \
      systemctl --quiet restart "$service_name" > /dev/null 2>&1 || :                                                                                            \
      rm -f "%{_localstatedir}/lib/rpm-state/$service_name/active" > /dev/null 2>&1 || :                                                                         \
    done                                                                                                                                                         \
  fi

%verifyscript
# check validity of xroad user and group
if [ "`id -u xroad`" -eq 0 ]; then
echo "The xroad system user must not have uid 0 (root). Please fix this and reinstall this package." >&2
exit 1
fi
if [ "`id -g xroad`" -eq 0 ]; then
echo "The xroad system user must not have root as primary group. Please fix this and reinstall this package." >&2
exit 1
fi

%post
umask 027

# ensure home directory ownership
mkdir -p /var/lib/xroad
su - xroad -c "test -O /var/lib/xroad && test -G /var/lib/xroad" || chown xroad:xroad /var/lib/xroad
chmod 0755 /var/lib/xroad
chmod -R go-w /var/lib/xroad

# nicer log directory permissions
mkdir -p /var/log/xroad
chmod -R go-w /var/log/xroad
chmod 1770 /var/log/xroad
chown xroad:adm /var/log/xroad

#tmp folder
mkdir -p /var/tmp/xroad
chmod 1750 /var/tmp/xroad
chown xroad:xroad /var/tmp/xroad

#local overrides
test -f /etc/xroad/services/local.properties || touch /etc/xroad/services/local.properties

chown -R xroad:xroad /etc/xroad/services/* /etc/xroad/conf.d/*
chmod -R o=rwX,g=rX,o= /etc/xroad/services/* /etc/xroad/conf.d/*

#enable xroad services by default
echo 'enable xroad-*.service' > %{_presetdir}/90-xroad.preset

%if 0%{?el7}
%set_default_java_version
%restart_xroad_services
%endif

%posttrans -p /bin/bash
%if 0%{?el8} || 0%{?el9}
%set_default_java_version
%restart_xroad_services
%endif

%changelog
