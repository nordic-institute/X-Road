# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-securityserver-fi
Version:            6.7
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road security server with Finnish settings
BuildArch:          noarch
Group:              Applications/Internet
License:            Proprietary
Requires:           xroad-securityserver >= %version
Conflicts:          xroad-centralserver

%define src %{_topdir}/..

%description
This is meta package of X-Road security server with Finnish settings

%clean

%prep

%build

%install
mkdir -p %{buildroot}/etc/xroad/conf.d
cp -p %{src}/../default-configuration/signer-fi.ini %{buildroot}/etc/xroad/conf.d/

%files
/etc/xroad/conf.d/signer-fi.ini

%post
cp -pf /etc/xroad/conf.d/signer-fi.ini /etc/xroad/conf.d/signer.ini
