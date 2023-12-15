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
Requires:  jre-17-headless, tzdata-java
Requires:  crudini, hostname, sudo, openssl

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
mkdir -p %{buildroot}/var/lib/xroad/backup
mkdir -p %{buildroot}/etc/xroad/backup.d

ln -s /usr/share/xroad/jlib/common-db-1.0.jar %{buildroot}/usr/share/xroad/jlib/common-db.jar
ln -s /usr/share/xroad/jlib/postgresql-42.5.4.jar %{buildroot}/usr/share/xroad/jlib/postgresql.jar
ln -s /usr/share/xroad/db/liquibase-core-4.19.0.jar %{buildroot}/usr/share/xroad/db/liquibase-core.jar

cp -p %{_sourcedir}/base/xroad-base.service %{buildroot}%{_unitdir}
cp -p %{srcdir}/../../../common/common-db/build/libs/common-db-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{srcdir}/../../../security-server/admin-service/application/build/unpacked-libs/postgresql-42.5.4.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{srcdir}/default-configuration/common.ini %{buildroot}/etc/xroad/conf.d/
cp -p %{srcdir}/../../../LICENSE.txt %{buildroot}/usr/share/doc/%{name}/LICENSE.txt
cp -p %{srcdir}/../../../3RD-PARTY-NOTICES.txt %{buildroot}/usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
cp -p %{srcdir}/common/base/usr/share/xroad/db/liquibase-core-4.19.0.jar %{buildroot}/usr/share/xroad/db/liquibase-core-4.19.0.jar
cp -p %{srcdir}/common/base/usr/share/xroad/db/liquibase.sh %{buildroot}/usr/share/xroad/db/liquibase.sh
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
%dir /var/lib/xroad/backup
%config /etc/xroad/services/global.conf
%config /etc/xroad/conf.d/common.ini
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
/usr/share/xroad/scripts/xroad-base.sh
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

if [ $1 -gt 1 ] ; then
    # upgrade
    if ! grep -q '\s*JAVA_HOME=' /etc/xroad/services/local.conf; then
      #6.26.0 migrate "JAVA_HOME" to local.conf
      java_home=$(grep '^JAVA_HOME=' /etc/xroad/services/global.conf);
      if [ -n "$java_home" ]; then
        echo "$java_home" >>/etc/xroad/services/local.conf
      fi
    fi

    # 7.4.0 remove JAVA_HOME from local.conf if it points to java < 17
    if [ -f /etc/xroad/services/local.conf ]; then
      java_home=$(grep -oP '^\s*JAVA_HOME=\K(.*)' /etc/xroad/services/local.conf | tail -n 1)
      if [ -n "$java_home" ]; then
        java_version=$("$java_home"/bin/java -version 2>&1 | grep -i version | cut -d '"' -f2 | cut -d. -f1)
        if [[ $java_version -lt 17 ]]; then
          sed -E -i 's/^(\s*JAVA_HOME=)/# \1/g' /etc/xroad/services/local.conf \
                  && echo "Removed JAVA_HOME from /etc/xroad/services/local.conf" >&2 \
                  || echo "Failed to remove JAVA_HOME from /etc/xroad/services/local.conf" >&2
        fi
      fi
    fi
fi

%define set_default_java_version()                                                                                         \
  if [ $1 -ge 1 ] ; then                                                                                                \
    `# 7.4.0. Check that the default java version is at least 17`                                                       \
    java_version_supported() {                                                                                          \
      local java_exec=$1                                                                                                \
      local java_version=$("$java_exec" -version 2>&1 | grep -i version | cut -d '"' -f2 | cut -d. -f1)                 \
      [[ $java_version -ge 17 ]]                                                                                        \
    }                                                                                                                   \
    if ! java_version_supported /etc/alternatives/java; then                                                            \
      if [ -x /etc/alternatives/jre_17/bin/java ] && java_version_supported /etc/alternatives/jre_17/bin/java; then     \
        echo "Configuring Java 17 as the default version..."                                                            \
        alternatives --set java $(readlink -f /etc/alternatives/jre_17)/bin/java                                        \
      else                                                                                                              \
        echo "Cannot find supported java version. Please set system default java installation with 'alternatives' command." >&2   \
      fi                                                                                                                 \
    fi                                                                                                                   \
                                                                                                                         \
    `# restart is required to trigger any changes within xroad-base.sh`                                                  \
    echo "Restarting xroad-base service.."                                                                               \
    %systemd_try_restart xroad-base.service                                                                              \
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
mkdir -p /var/lib/xroad/backup
su - xroad -c "test -O /var/lib/xroad && test -G /var/lib/xroad" || chown xroad:xroad /var/lib/xroad
chown xroad:xroad /var/lib/xroad/backup
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
test -f /etc/xroad/conf.d/local.ini || touch /etc/xroad/conf.d/local.ini

chown -R xroad:xroad /etc/xroad/services/* /etc/xroad/conf.d/*
chmod -R o=rwX,g=rX,o= /etc/xroad/services/* /etc/xroad/conf.d/*

#enable xroad services by default
echo 'enable xroad-*.service' > %{_presetdir}/90-xroad.preset

%if 0%{?el7}
%set_default_java_version
%restart_xroad_services
%endif

%posttrans -p /bin/bash
%if 0%{?el8}
%set_default_java_version
%restart_xroad_services
%endif

%changelog
