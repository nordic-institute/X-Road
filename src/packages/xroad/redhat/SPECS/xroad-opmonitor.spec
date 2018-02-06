# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)
# Ignore python bytecompile errors due to mismatching python versions.
# This is to be able to provide sample scripts with the package with no hassle.
%global _python_bytecompile_errors_terminate_build 0

Name:               xroad-opmonitor
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road operations monitoring daemon
Group:              Applications/Internet
License:            MIT
BuildRequires:      systemd
Requires(post):     systemd
Requires(preun):    systemd
Requires(postun):   systemd
Requires:           xroad-common = %version-%release, postgresql-server, postgresql-contrib

%define src %{_topdir}/..

%description
X-Road operations monitoring daemon

%prep

%build

%install
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}/usr/share/xroad/db/op-monitor/
mkdir -p %{buildroot}/usr/share/xroad/scripts/
mkdir -p %{buildroot}/usr/share/xroad/jlib/
mkdir -p %{buildroot}/usr/share/doc/%{name}
mkdir -p %{buildroot}/usr/bin/
mkdir -p %{buildroot}/etc/xroad/services/
mkdir -p %{buildroot}/usr/share/xroad/bin/
mkdir -p %{buildroot}/etc/xroad/conf.d/
mkdir -p %{buildroot}/etc/xroad/backup.d/
mkdir -p %{buildroot}/usr/share/doc/xroad-opmonitor/examples/zabbix/

cp -p %{_sourcedir}/opmonitor/xroad-opmonitor %{buildroot}/usr/share/xroad/bin/
cp -p %{_sourcedir}/opmonitor/xroad-opmonitor.service %{buildroot}%{_unitdir}
cp -p %{_sourcedir}/opmonitor/xroad-opmonitor-initdb.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{src}/../../op-monitor-daemon/build/libs/op-monitor-daemon-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{src}/../default-configuration/op-monitor.ini %{buildroot}/etc/xroad/conf.d/
cp -p %{src}/../default-configuration/op-monitor-logback.xml %{buildroot}/etc/xroad/conf.d/
cp -p %{src}/op-monitor/etc/xroad/services/opmonitor.conf %{buildroot}/etc/xroad/services/
cp -p %{src}/op-monitor/usr/share/xroad/db/op-monitor/*.xml %{buildroot}/usr/share/xroad/db/op-monitor/
cp -p %{src}/op-monitor/usr/share/xroad/db/op-monitor-changelog.xml %{buildroot}/usr/share/xroad/db/
cp -p %{src}/op-monitor/generate-opmonitor-certificate.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{src}/../../LICENSE.txt %{buildroot}/usr/share/doc/xroad-opmonitor/
cp -p %{src}/../../securityserver-LICENSE.info %{buildroot}/usr/share/doc/xroad-opmonitor/
cp -p %{src}/../../systemtest/op-monitoring/zabbix_api/examples/zabbix/* %{buildroot}/usr/share/doc/xroad-opmonitor/examples/zabbix/
cp -p %{src}/../../../CHANGELOG.md %{buildroot}/usr/share/doc/xroad-opmonitor/
cp -p %{src}/op-monitor/etc/xroad/backup.d/??_xroad-opmonitor %{buildroot}/etc/xroad/backup.d/

ln -s /usr/share/xroad/jlib/op-monitor-daemon-1.0.jar %{buildroot}/usr/share/xroad/jlib/op-monitor-daemon.jar
ln -s /usr/share/uxp/scripts/generate-opmonitor-certificate.sh %{buildroot}/usr/bin/generate-opmonitor-certificate

%clean
rm -rf %{buildroot}

%files
%defattr(-,xroad,xroad,-)
%config /etc/xroad/conf.d/op-monitor.ini
%config /etc/xroad/conf.d/op-monitor-logback.xml
%config /etc/xroad/services/opmonitor.conf
%attr(0440,xroad,xroad) %config /etc/xroad/backup.d/??_xroad-opmonitor

/usr/share/xroad/db/op-monitor/
/usr/share/xroad/db/op-monitor-changelog.xml

%attr(540,root,xroad) /usr/share/xroad/scripts/xroad-opmonitor-initdb.sh
%attr(754,xroad,xroad) /usr/share/xroad/bin/xroad-opmonitor
%attr(644,root,root) %{_unitdir}/xroad-opmonitor.service

/usr/share/xroad/jlib/op-monitor-daemon-*.jar
/usr/share/xroad/jlib/op-monitor-daemon.jar
/usr/share/xroad/scripts/generate-opmonitor-certificate.sh
/usr/bin/generate-opmonitor-certificate

%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/securityserver-LICENSE.info
%doc /usr/share/doc/%{name}/examples/zabbix/*
%doc /usr/share/doc/%{name}/CHANGELOG.md

%pre

%post
%systemd_post xroad-opmonitor.service
if [ $1 -eq 1 ] ; then
    /usr/share/xroad/scripts/xroad-opmonitor-initdb.sh
fi

%preun
%systemd_preun xroad-opmonitor.service

%postun
%systemd_postun_with_restart xroad-opmonitor.service

%changelog

