%include %{_specdir}/common.inc
# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-ds-identity-hub
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road Data Space Identity Hub
Group:              Applications/Internet
License:            MIT
Requires(post):     systemd
Requires(preun):    systemd
Requires(postun):   systemd
BuildRequires:      systemd
Requires:           systemd
Requires:           xroad-base = %version-%release
Requires:           (xroad-secret-store-local = %version-%release or xroad-secret-store-remote = %version-%release)

%define src %{_topdir}/..

%description
X-Road Data Space Identity Hub service

%prep
rm -rf ds-identity-hub
cp -a %{srcdir}/common/ds-identity-hub .

%build

%install
cd ds-identity-hub
cp -a * %{buildroot}

mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}/usr/share/xroad/jlib
mkdir -p %{buildroot}/usr/share/xroad/jlib/ds-identity-hub
mkdir -p %{buildroot}/usr/share/xroad/scripts
mkdir -p %{buildroot}/usr/share/xroad/db
mkdir -p %{buildroot}/etc/xroad
mkdir -p %{buildroot}/etc/xroad/services
mkdir -p %{buildroot}/usr/share/xroad/bin
mkdir -p %{buildroot}/usr/share/doc/%{name}

cp -p %{_sourcedir}/ds-identity-hub/xroad-ds-identity-hub.service %{buildroot}%{_unitdir}
cp -p -r %{srcdir}/../../../../src/service/ds-identity-hub/ds-identity-hub-application/build/quarkus-app/* %{buildroot}/usr/share/xroad/jlib/ds-identity-hub/
cp -p %{srcdir}/../../../../src/LICENSE.txt %{buildroot}/usr/share/doc/%{name}/LICENSE.txt
cp -p %{srcdir}/../../../../src/3RD-PARTY-NOTICES.txt %{buildroot}/usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/%{name}/CHANGELOG.md

ln -s /usr/share/xroad/jlib/ds-identity-hub/quarkus-run.jar %{buildroot}/usr/share/xroad/jlib/ds-identity-hub.jar

%clean
rm -rf %{buildroot}

%files
%defattr(0640,xroad,xroad,0751)
%config /etc/xroad/services/ds-identity-hub.conf

%defattr(-,root,root,-)
%attr(644,root,root) %{_unitdir}/xroad-ds-identity-hub.service

%attr(550,root,xroad) /usr/share/xroad/bin/xroad-ds-identity-hub
%attr(550,root,xroad) /usr/share/xroad/scripts/setup_ds_identityhub_db.sh

/usr/share/xroad/jlib/ds-identity-hub.jar
/usr/share/xroad/jlib/ds-identity-hub/
/usr/share/xroad/db/
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
%doc /usr/share/doc/%{name}/CHANGELOG.md

%pre -p /bin/bash
%upgrade_check

mkdir -p %{_localstatedir}/lib/rpm-state/%{name}
if systemctl is-active %{name} &> /dev/null; then
  touch "%{_localstatedir}/lib/rpm-state/%{name}/active"
fi

%define init_xroad_ds_identity_hub_db()                       \
    /usr/share/xroad/scripts/setup_ds_identityhub_db.sh "" || exit $?

%post -p /bin/bash
umask 027

%systemd_post xroad-ds-identity-hub.service

%preun
%systemd_preun xroad-ds-identity-hub.service

%postun
%systemd_postun_with_restart xroad-ds-identity-hub.service

%posttrans -p /bin/bash
umask 027

%init_xroad_ds_identity_hub_db

%changelog
