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

