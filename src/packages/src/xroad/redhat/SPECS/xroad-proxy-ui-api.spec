# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

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

cp -p %{_sourcedir}/proxy-ui-api/xroad-proxy-ui-api-setup.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{_sourcedir}/proxy-ui-api/xroad-proxy-ui-api.service %{buildroot}%{_unitdir}
cp -p %{srcdir}/../../../proxy-ui-api/build/libs/proxy-ui-api-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{srcdir}/default-configuration/proxy-ui-api.ini %{buildroot}/etc/xroad/conf.d
cp -p %{srcdir}/default-configuration/proxy-ui-api-logback.xml %{buildroot}/etc/xroad/conf.d
cp -p %{srcdir}/../../../LICENSE.txt %{buildroot}/usr/share/doc/%{name}/LICENSE.txt
cp -p %{srcdir}/../../../securityserver-LICENSE.info %{buildroot}/usr/share/doc/%{name}/securityserver-LICENSE.info
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/%{name}/CHANGELOG.md

ln -s /usr/share/xroad/jlib/proxy-ui-api-1.0.jar %{buildroot}/usr/share/xroad/jlib/proxy-ui-api.jar

%clean
rm -rf %{buildroot}

%files
%defattr(0640,xroad,xroad,0751)
%config /etc/xroad/services/proxy-ui-api.conf
%config /etc/xroad/conf.d/proxy-ui-api.ini
%config /etc/xroad/conf.d/proxy-ui-api-logback.xml

%attr(644,root,root) %{_unitdir}/xroad-proxy-ui-api.service

%attr(755,root,root) /usr/share/xroad/bin/xroad-proxy-ui-api

%attr(540,root,xroad) /usr/share/xroad/scripts/xroad-proxy-ui-api-setup.sh

%defattr(-,root,root,-)
/usr/share/xroad/jlib/proxy-ui-api*.jar
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/securityserver-LICENSE.info
%doc /usr/share/doc/%{name}/CHANGELOG.md

%pre
service xroad-jetty stop || true

%post
%systemd_post xroad-proxy-ui-api.service

# show nginx status
echo xroad-proxy-ui-api.spec.post 1
service nginx status | head -3
date
sleep 5
date

# restart nginx only if it was running
echo restarting nginx from xroad-proxy-ui-api.post
service nginx status && service nginx restart

echo xroad-proxy-ui-api.spec.post 2
service nginx status | head -3
date
sleep 5
date

/usr/share/xroad/scripts/xroad-proxy-ui-api-setup.sh

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

#migrating possible local configuration for modified configuration values (for version 6.24.0)
migrate_conf_value /etc/xroad/conf.d/local.ini proxy-ui wsdl-validator-command proxy-ui-api wsdl-validator-command
migrate_conf_value /etc/xroad/conf.d/local.ini proxy-ui auth-cert-reg-signature-digest-algorithm-id proxy-ui-api auth-cert-reg-signature-digest-algorithm-id

%preun
%systemd_preun xroad-proxy-ui-api.service

%postun
%systemd_postun_with_restart xroad-proxy-ui-api.service

%changelog
