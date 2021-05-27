%include %{_specdir}/common.inc
# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:       xroad-addon-opmonitoring
Version:    %{xroad_version}
Release:    %{rel}%{?snapshot}%{?dist}
Summary:    X-Road AddOn: opmonitoring
Group:      Applications/Internet
License:    MIT
Requires:   xroad-proxy = %version-%release, xroad-opmonitor = %version-%release

%define src %{_topdir}/..

%description
AddOn for operations monitoring.

%prep

%build

%install
mkdir -p %{buildroot}/usr/share/xroad/jlib/addon/proxy/
mkdir -p %{buildroot}/usr/share/doc/%{name}

cp -p %{srcdir}/common/addon/proxy/opmonitoring.conf %{buildroot}/usr/share/xroad/jlib/addon/proxy/
cp -p %{srcdir}/../../../addons/op-monitoring/build/libs/op-monitoring-1.0.jar %{buildroot}/usr/share/xroad/jlib/addon/proxy/
cp -p %{srcdir}/../../../LICENSE.txt %{buildroot}/usr/share/doc/xroad-addon-opmonitoring/
cp -p %{srcdir}/../../../3RD-PARTY-NOTICES.txt %{buildroot}/usr/share/doc/xroad-addon-opmonitoring/
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/xroad-addon-opmonitoring/

%clean
rm -rf %{buildroot}

%files
%defattr(-,root,root,-)
/usr/share/xroad/jlib/addon/proxy/op-monitoring-1.0.jar
/usr/share/xroad/jlib/addon/proxy/opmonitoring.conf
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
%doc /usr/share/doc/%{name}/CHANGELOG.md

%pre -p /bin/bash
%upgrade_check

%postun
%systemd_postun_with_restart xroad-proxy.service

%changelog

