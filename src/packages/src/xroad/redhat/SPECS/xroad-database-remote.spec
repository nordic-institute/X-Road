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

%pre
version_lt () {
    newest=$( ( echo "$1"; echo "$2" ) | sort -V | tail -n1)
    [ "$1" != "$newest" ]
}
if [ $1 -gt 1 ] ; then
    # upgrade
    installed_version_full=$(rpm -q xroad-database-remote --queryformat="%{VERSION}-%{RELEASE}")
    incoming_version_full=$(echo "%{version}-%{release}")
    if [[ "$incoming_version_full" == 7* ]]; then
        last_supported_version=$(echo "$installed_version_full" | awk -F. '{print $1"."($2+2)}')
        incoming_version=$(echo "$incoming_version_full" | awk -F. '{print $1"."$2}')
        if version_lt $last_supported_version $incoming_version ; then
          echo "This package can be upgraded up to version $last_supported_version.x"
          exit 1
        fi
    fi
fi

%post
