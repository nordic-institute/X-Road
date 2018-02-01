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
Requires:   xroad-base = %version-%release, xroad-nginx = %version-%release, xroad-confclient = %version-%release, xroad-signer = %version-%release

%define src %{_topdir}/..

%description
Meta-package for X-Road shared components and utilities

%prep

%build

%install

%clean
rm -rf %{buildroot}

%files

%pre

%post
