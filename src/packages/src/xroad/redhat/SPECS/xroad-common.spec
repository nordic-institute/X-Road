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

%define src %{_topdir}/..

%description
Obsolete meta-package for X-Road shared components and utilities. To be removed in future versions.

%prep

%build

%install

%clean
rm -rf %{buildroot}

%files

%pre

%post
