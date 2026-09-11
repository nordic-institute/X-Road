%include %{_specdir}/common.inc
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-secret-store-local-repo
BuildArch:          noarch
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            OpenBao DNF repository and version gate for X-Road
Group:              Applications/Internet
License:            MIT
Requires:           xroad-base = %version-%release
Conflicts:          xroad-secret-store-remote

%description
Configures the OpenBao DNF repository and version gate.

%clean
rm -rf %{buildroot}

%prep

%build

%install
mkdir -p %{buildroot}/usr/share/xroad/scripts/

cp -p %{srcdir}/../../../.scripts/configure-mirror-openbao-rpm.sh %{buildroot}/usr/share/xroad/scripts/configure-mirror-openbao.sh
cp -p %{srcdir}/../../../.scripts/update-openbao-version-rpm.sh %{buildroot}/usr/share/xroad/scripts/update-openbao-version.sh

%files
%defattr(0640,xroad,xroad,0751)
%attr(554,root,xroad) /usr/share/xroad/scripts/configure-mirror-openbao.sh
%attr(554,root,xroad) /usr/share/xroad/scripts/update-openbao-version.sh

%pre

%upgrade_check

%post -p /bin/bash
OPENBAO_REPO_FILE="/etc/yum.repos.d/openbao.repo"
if [ -f "$OPENBAO_REPO_FILE" ]; then
    # Repo exists — just refresh the version gate, leave baseurl/creds as-is.
    UPDATE_OPENBAO_VERSION="/usr/share/xroad/scripts/update-openbao-version.sh"
    [ -x "$UPDATE_OPENBAO_VERSION" ] && "$UPDATE_OPENBAO_VERSION"
else
    # No repo yet — full first-time configuration.
    CONFIGURE_OPENBAO_REPO="/usr/share/xroad/scripts/configure-mirror-openbao.sh"
    [ -x "$CONFIGURE_OPENBAO_REPO" ] && "$CONFIGURE_OPENBAO_REPO"
fi
