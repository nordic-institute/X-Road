%include %{_specdir}/common.inc
# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-ds-data-plane
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road Data Space Data Plane
Group:              Applications/Internet
License:            MIT
Requires(post):     systemd
Requires(preun):    systemd
Requires(postun):   systemd
BuildRequires:      systemd
Requires:           systemd
Requires:           xroad-base = %version-%release
Requires:           (xroad-secret-store-local = %version-%release or xroad-secret-store-remote = %version-%release)

%define src %{_topdir}/..

%description
X-Road Data Space Data Plane service

%prep
rm -rf ds-data-plane
cp -a %{srcdir}/common/ds-data-plane .

%build

%install
cd ds-data-plane
cp -a * %{buildroot}

mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}/usr/share/xroad/jlib
mkdir -p %{buildroot}/usr/share/xroad/jlib/ds-data-plane
mkdir -p %{buildroot}/usr/share/xroad/scripts
mkdir -p %{buildroot}/usr/share/xroad/db
mkdir -p %{buildroot}/etc/xroad
mkdir -p %{buildroot}/etc/xroad/services
mkdir -p %{buildroot}/usr/share/xroad/bin
mkdir -p %{buildroot}/usr/share/doc/%{name}

cp -p %{_sourcedir}/ds-data-plane/xroad-ds-data-plane.service %{buildroot}%{_unitdir}
cp -p -r %{srcdir}/../../../../src/service/ds-data-plane/ds-data-plane-application/build/quarkus-app/* %{buildroot}/usr/share/xroad/jlib/ds-data-plane/
cp -p -r %{srcdir}/../../../../src/service/ds-data-plane/ds-data-plane-db/build/resources/main/liquibase/* %{buildroot}/usr/share/xroad/db/
cp -p %{srcdir}/../../../../src/LICENSE.txt %{buildroot}/usr/share/doc/%{name}/LICENSE.txt
cp -p %{srcdir}/../../../../src/3RD-PARTY-NOTICES.txt %{buildroot}/usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/%{name}/CHANGELOG.md

ln -s /usr/share/xroad/jlib/ds-data-plane/quarkus-run.jar %{buildroot}/usr/share/xroad/jlib/ds-data-plane.jar

%clean
rm -rf %{buildroot}

%files
%defattr(0640,xroad,xroad,0751)
%config /etc/xroad/services/ds-data-plane.conf

%defattr(-,root,root,-)
%attr(644,root,root) %{_unitdir}/xroad-ds-data-plane.service

%attr(550,root,xroad) /usr/share/xroad/bin/xroad-ds-data-plane
%attr(550,root,xroad) /usr/share/xroad/scripts/setup_ds_dataplane_db.sh

/usr/share/xroad/jlib/ds-data-plane.jar
/usr/share/xroad/jlib/ds-data-plane/
/usr/share/xroad/db/
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
%doc /usr/share/doc/%{name}/CHANGELOG.md

%pre -p /bin/bash
%upgrade_check

mkdir -p %{_localstatedir}/lib/rpm-state/%{name}
if systemctl is-active %{name} &> /dev/null; then
  touch "%{_localstatedir}/lib/rpm-state/%{name}/active"
fi

%post -p /bin/bash
umask 027

# Run database setup script
/usr/share/xroad/scripts/setup_ds_dataplane_db.sh "" || true

# Temporary dev flow - copy admin credentials from xroad.properties to db.properties
# This allows the application to use admin credentials for database access
# Also updates JDBC URL with currentSchema for EDC compatibility
# To be removed before release
db_name="ds-data-plane"
root_properties=/etc/xroad.properties
db_properties=/etc/xroad/db.properties

if [ -f "$root_properties" ] && [ -f "$db_properties" ]; then
  admin_user=$(crudini --get "$root_properties" '' "${db_name}.database.admin_user" 2>/dev/null)
  admin_pass=$(crudini --get "$root_properties" '' "${db_name}.database.admin_password" 2>/dev/null)
  if [ -n "$admin_user" ]; then
    crudini --set "$db_properties" '' "xroad.db.${db_name}.hibernate.connection.username" "$admin_user"
    crudini --set "$db_properties" '' "xroad.db.${db_name}.hibernate.connection.password" "$admin_pass"
    
    # Update JDBC URL with currentSchema for EDC compatibility
    current_url=$(crudini --get "$db_properties" '' "xroad.db.${db_name}.hibernate.connection.url" 2>/dev/null)
    if [ -n "$current_url" ] && [[ "$current_url" != *"currentSchema"* ]]; then
      schema=$(crudini --get "$db_properties" '' "xroad.db.${db_name}.hibernate.hikari.dataSource.currentSchema" 2>/dev/null)
      schema="${schema%%,*}"
      schema="${schema:-$db_name}"
      
      if [[ "$current_url" == *"?"* ]]; then
        new_url="${current_url}&currentSchema=${schema}"
      else
        new_url="${current_url}?currentSchema=${schema}"
      fi
      crudini --set "$db_properties" '' "xroad.db.${db_name}.hibernate.connection.url" "$new_url"
    fi
  fi
fi

%systemd_post xroad-ds-data-plane.service

%preun
%systemd_preun xroad-ds-data-plane.service

%postun
%systemd_postun_with_restart xroad-ds-data-plane.service

%changelog
