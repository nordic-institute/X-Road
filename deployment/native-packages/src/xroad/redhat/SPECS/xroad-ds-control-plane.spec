%include %{_specdir}/common.inc
# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-ds-control-plane
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road Data Space Control Plane
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
X-Road Data Space Control Plane service

%prep
rm -rf ds-control-plane
cp -a %{srcdir}/common/ds-control-plane .

%build

%install
cd ds-control-plane
cp -a * %{buildroot}

mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}/usr/share/xroad/jlib
mkdir -p %{buildroot}/usr/share/xroad/jlib/ds-control-plane
mkdir -p %{buildroot}/usr/share/xroad/scripts
mkdir -p %{buildroot}/usr/share/xroad/db
mkdir -p %{buildroot}/etc/xroad
mkdir -p %{buildroot}/etc/xroad/services
mkdir -p %{buildroot}/usr/share/xroad/bin
mkdir -p %{buildroot}/usr/share/doc/%{name}

cp -p %{_sourcedir}/ds-control-plane/xroad-ds-control-plane.service %{buildroot}%{_unitdir}
cp -p -r %{srcdir}/../../../../src/service/ds-control-plane/ds-control-plane-application/build/quarkus-app/* %{buildroot}/usr/share/xroad/jlib/ds-control-plane/
cp -p %{srcdir}/../../../../src/LICENSE.txt %{buildroot}/usr/share/doc/%{name}/LICENSE.txt
cp -p %{srcdir}/../../../../src/3RD-PARTY-NOTICES.txt %{buildroot}/usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/%{name}/CHANGELOG.md

ln -s /usr/share/xroad/jlib/ds-control-plane/quarkus-run.jar %{buildroot}/usr/share/xroad/jlib/ds-control-plane.jar

%clean
rm -rf %{buildroot}

%files
%defattr(0640,xroad,xroad,0751)
%config /etc/xroad/services/ds-control-plane.conf

%defattr(-,root,root,-)
%attr(644,root,root) %{_unitdir}/xroad-ds-control-plane.service

%attr(550,root,xroad) /usr/share/xroad/bin/xroad-ds-control-plane
%attr(550,root,xroad) /usr/share/xroad/scripts/setup_ds_controlplane_db.sh

/usr/share/xroad/jlib/ds-control-plane.jar
/usr/share/xroad/jlib/ds-control-plane/
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

%define init_xroad_ds_control_plane_db()                       \
    /usr/share/xroad/scripts/setup_ds_controlplane_db.sh "" || exit $?

%post -p /bin/bash
umask 027

%systemd_post xroad-ds-control-plane.service

%preun
%systemd_preun xroad-ds-control-plane.service

%postun
%systemd_postun_with_restart xroad-ds-control-plane.service

%posttrans -p /bin/bash
umask 027

%init_xroad_ds_control_plane_db

%changelog
