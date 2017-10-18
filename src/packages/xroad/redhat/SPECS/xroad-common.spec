# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:       xroad-common
Version:    %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:    %{rel}%{?snapshot}%{?dist}
Summary:    X-Road shared components
Group:      Applications/Internet
License:    MIT
Requires(post): systemd
Requires(preun): systemd
Requires(postun): systemd
BuildRequires: systemd
Requires:  systemd
Requires:  postgresql-server, postgresql-contrib
Requires: xroad-base >= %version, xroad-nginx >= %version, xroad-confclient >= %version, xroad-signer >= %version

%define src %{_topdir}/..

%description
X-Road shared components and utilities

%prep

%build

%install

%clean
rm -rf %{buildroot}

%files

%pre

%post


