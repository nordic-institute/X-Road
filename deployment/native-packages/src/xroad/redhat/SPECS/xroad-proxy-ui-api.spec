%include %{_specdir}/common.inc

Name:               xroad-proxy-ui-api
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road proxy UI REST API
Group:              Applications/Internet
License:            MIT
BuildRequires:      systemd
Requires(post):     systemd
Requires(preun):    systemd
Requires(postun):   systemd
Requires:           iproute, hostname
Requires:           xroad-base = %version-%release, xroad-proxy = %version-%release
Obsoletes:          xroad-nginx, xroad-jetty9

%define src %{_topdir}/..

%description
REST API for X-Road proxy UI and management operations

%prep
rm -rf proxy-ui-api
cp -a %{srcdir}/common/proxy-ui-api .

%build

%install
cd proxy-ui-api
cp -a * %{buildroot}

mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}/usr/share/xroad/jlib
mkdir -p %{buildroot}/usr/share/xroad/scripts
mkdir -p %{buildroot}/usr/share/doc/%{name}
mkdir -p %{buildroot}/etc/xroad/conf.d

cp -p %{_sourcedir}/proxy-ui-api/xroad-proxy-ui-api.service %{buildroot}%{_unitdir}
cp -p %{srcdir}/../../../../src/security-server/admin-service/application/build/libs/proxy-ui-api-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{srcdir}/default-configuration/acme.example.yml %{buildroot}/etc/xroad/conf.d
cp -p %{srcdir}/default-configuration/mail.example.yml %{buildroot}/etc/xroad/conf.d
cp -p %{srcdir}/../../../../src/LICENSE.txt %{buildroot}/usr/share/doc/%{name}/LICENSE.txt
cp -p %{srcdir}/../../../../src/3RD-PARTY-NOTICES.txt %{buildroot}/usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/%{name}/CHANGELOG.md

ln -s /usr/share/xroad/jlib/proxy-ui-api-1.0.jar %{buildroot}/usr/share/xroad/jlib/proxy-ui-api.jar

%clean
rm -rf %{buildroot}

%files
%defattr(0640,xroad,xroad,0751)
%config /etc/xroad/services/proxy-ui-api.conf
%config /etc/xroad/conf.d/acme.example.yml
%config /etc/xroad/conf.d/mail.example.yml
%attr(644,root,root) %{_unitdir}/xroad-proxy-ui-api.service
%attr(755,root,root) /usr/share/xroad/bin/xroad-proxy-ui-api
%defattr(-,root,root,-)
/usr/share/xroad/jlib/proxy-ui-api*.jar
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
%doc /usr/share/doc/%{name}/CHANGELOG.md
/usr/share/xroad/scripts/acme_contacts_and_keystore_pw_migra.sh

%pre -p /bin/bash
%upgrade_check

mkdir -p %{_localstatedir}/lib/rpm-state/%{name}
if systemctl is-active %{name} &> /dev/null; then
  touch "%{_localstatedir}/lib/rpm-state/%{name}/active"
fi

if [ "$1" -gt 1 ]; then
  rpm -q %{name} --queryformat="%%{version}" &> "%{_localstatedir}/lib/rpm-state/%{name}/prev-version"

  systemctl --quiet stop xroad-jetty.service >/dev/null 2>&1 || true
fi

%post
%systemd_post xroad-proxy-ui-api.service

#parameters:
#1 file_path
#2 old_section
#3 old_key
#4 new_section
#5 new_key
function migrate_conf_value {
    MIGRATION_VALUE="$(crudini --get "$1" "$2" "$3" 2>/dev/null || true)"
    if [ "${MIGRATION_VALUE}" ];
        then
            crudini --set "$1" "$4" "$5" "${MIGRATION_VALUE}"
            echo Configuration migration: "$2"."$3" "->" "$4"."$5"
            crudini --del "$1" "$2" "$3"
    fi
}

if [ $1 -gt 1 ] ; then
  #migrating possible local configuration for modified configuration values (for version 6.24.0)
  migrate_conf_value /etc/xroad/conf.d/local.ini proxy-ui auth-cert-reg-signature-digest-algorithm-id proxy-ui-api auth-cert-reg-signature-digest-algorithm-id

  prev_version=$(cat %{_localstatedir}/lib/rpm-state/%{name}/prev-version)

  # disable strict-identifier-checks for upgrades from version < 7.3.0
  if ! echo -e "7.3.0\n$prev_version" | sort -V -C; then
      crudini --set /etc/xroad/conf.d/local.ini proxy-ui-api strict-identifier-checks false
  fi

  if ! echo -e "7.6.0\n$prev_version" | sort -V -C; then
    /usr/share/xroad/scripts/acme_contacts_and_keystore_pw_migra.sh
  fi

  rm -f "%{_localstatedir}/lib/rpm-state/%{name}/prev-version" >/dev/null 2>&1 || :
fi

%preun
%systemd_preun xroad-proxy-ui-api.service

%postun
%systemd_postun_with_restart xroad-proxy-ui-api.service

%changelog
