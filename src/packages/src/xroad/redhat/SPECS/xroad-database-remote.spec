# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-database-remote
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            Meta-package for remote database dependencies
BuildArch:          noarch
Group:              Applications/Internet
License:            MIT
Requires:           xroad-base = %version-%release, postgresql
Provides:           xroad-database = %version-%release
Conflicts:          xroad-database-local

%description
This is meta package for remote database dependencies

%clean

%prep

%build

%install

%files

%post
