%include %{_specdir}/common.inc
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-database-local
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            Meta-package for local database dependencies
BuildArch:          noarch
Group:              Applications/Internet
License:            MIT
Requires:           xroad-base = %version-%release, postgresql-server, postgresql-contrib
Conflicts:          xroad-database-remote
Provides:           xroad-database = %version-%{release}.1

%description
This is meta package for local database dependencies

%clean

%prep

%build

%install
mkdir -p %{buildroot}/usr/share/xroad/scripts

cp -p %{_sourcedir}/database-local/xroad-init-local-postgres.sh %{buildroot}/usr/share/xroad/scripts/

%files
%attr(540,root,root) /usr/share/xroad/scripts/xroad-init-local-postgres.sh

%pre -p /bin/bash
%upgrade_check

%post -p /bin/bash
if [ $1 -eq 1 ] ; then
  # Initial installation
  if ! /usr/share/xroad/scripts/xroad-init-local-postgres.sh; then
    echo "Error: Failed to initialize DB."
    exit 1
  fi
fi
