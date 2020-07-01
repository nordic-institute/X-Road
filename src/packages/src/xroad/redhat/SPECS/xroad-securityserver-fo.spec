# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-securityserver-fo
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road security server with the Faroe Island settings
BuildArch:          noarch
Group:              Applications/Internet
License:            MIT
Requires:           xroad-securityserver = %version-%release, xroad-addon-opmonitoring = %version-%release
Conflicts:          xroad-centralserver

%define src %{_topdir}/..

%description
This is meta package of X-Road security server with the Faroe Island settings

%clean

%prep

%build

%install
mkdir -p %{buildroot}/etc/xroad/conf.d
cp -p %{srcdir}/default-configuration/override-securityserver-fo.ini %{buildroot}/etc/xroad/conf.d/

%files
%defattr(-,xroad,xroad,-)
%config /etc/xroad/conf.d/override-securityserver-fo.ini

%post

