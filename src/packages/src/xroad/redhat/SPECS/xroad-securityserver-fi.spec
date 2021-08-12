%include %{_specdir}/common.inc
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-securityserver-fi
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road security server with Finnish settings
BuildArch:          noarch
Group:              Applications/Internet
License:            MIT
Requires:           xroad-securityserver = %version-%release, xroad-addon-opmonitoring = %version-%release
Conflicts:          xroad-centralserver

%define src %{_topdir}/..

%description
This is meta package of X-Road security server with Finnish settings

%clean

%prep

%build

%install
mkdir -p %{buildroot}/etc/xroad/conf.d
cp -p %{srcdir}/default-configuration/override-securityserver-fi.ini %{buildroot}/etc/xroad/conf.d/

%files
%defattr(-,xroad,xroad,-)
%config /etc/xroad/conf.d/override-securityserver-fi.ini

%pre -p /bin/bash
%upgrade_check

%post

%postun
if [ $1 -gt 0 ] ; then
    %systemd_postun_with_restart xroad-signer.service xroad-proxy.service xroad-addon-messagelog.service
fi
