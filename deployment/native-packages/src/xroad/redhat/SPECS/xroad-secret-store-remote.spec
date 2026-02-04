%include %{_specdir}/common.inc
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-secret-store-remote
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            Meta-package for remote secret store dependencies
Group:              Applications/Internet
License:            MIT
Requires:           xroad-base = %version-%release
Conflicts:          xroad-secret-store-local

%description
Prevents local installation of OpenBao when it is hosted remotely

%clean

%prep

%build

%install

%files

%pre -p /bin/bash
%upgrade_check

%post
