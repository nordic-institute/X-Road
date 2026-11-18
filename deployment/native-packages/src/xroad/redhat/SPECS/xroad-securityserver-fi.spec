%include %{_specdir}/common.inc
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-securityserver-fi
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road security server with Finnish settings
BuildArch:          noarch
Group:              Applications/Internet
License:            MIT
Requires:           xroad-securityserver = %version-%release, xroad-opmonitor = %version-%release
Conflicts:          xroad-centralserver
Conflicts:          xroad-securityserver-is
Conflicts:          xroad-securityserver-fo

%define src %{_topdir}/..

%description
This is meta package of X-Road security server with Finnish settings

%clean

%prep

%build

%install
mkdir -p %{buildroot}/usr/share/xroad
cp -p %{srcdir}/common/securityserver-fi/usr/share/xroad/country-profile %{buildroot}/usr/share/xroad/

%files
%attr(0644,root,root) /usr/share/xroad/country-profile

%pre -p /bin/bash
%upgrade_check

%post
if [ "$1" -eq 1 ]; then
  systemctl try-restart xroad-signer.service xroad-confclient.service xroad-proxy.service xroad-proxy-ui-api.service xroad-monitor.service xroad-opmonitor.service xroad-auxiliary-service.service xroad-ds-control-plane.service xroad-ds-identity-hub.service > /dev/null 2>&1 || :
fi
