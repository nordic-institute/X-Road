# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-securityserver
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road security server
BuildArch:          noarch
Group:              Applications/Internet
License:            MIT
Requires:           xroad-proxy = %version-%release
Requires:           xroad-proxy-ui-api = %version-%release
Requires:           xroad-addon-messagelog = %version-%release
Requires:           xroad-addon-metaservices = %version-%release
Requires:           xroad-addon-proxymonitor = %version-%release
Requires:           xroad-addon-wsdlvalidator = %version-%release
Conflicts:          xroad-centralserver

%description
This is meta package of X-Road security server

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
    installed_version_full=$(rpm -q xroad-securityserver --queryformat="%{VERSION}-%{RELEASE}")
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
