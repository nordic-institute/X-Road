%include %{_specdir}/common.inc
# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:       xroad-signer
Version:    %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:    %{rel}%{?snapshot}%{?dist}
Summary:    X-Road base components
Group:      Applications/Internet
License:    MIT
Requires(post): systemd
Requires(preun): systemd
Requires(postun): systemd
BuildRequires: systemd
Requires:  systemd
Requires: xroad-base = %version-%release
Requires: (xroad-secret-store-local = %version-%release or xroad-secret-store-remote = %version-%release)

%define src %{_topdir}/..

%description
X-Road signer component

%prep
rm -rf signer
cp -a %{srcdir}/common/signer .

%build

%install
cd signer
cp -a * %{buildroot}

mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}/usr/share/xroad/jlib
mkdir -p %{buildroot}/usr/share/xroad/jlib/signer
mkdir -p %{buildroot}/usr/share/xroad/jlib/signer-console
mkdir -p %{buildroot}/usr/share/xroad/lib
mkdir -p %{buildroot}/etc/xroad
mkdir -p %{buildroot}/etc/xroad/services
mkdir -p %{buildroot}/etc/xroad/conf.d
mkdir -p %{buildroot}/etc/xroad/ssl
mkdir -p %{buildroot}/etc/xroad/signer
mkdir -p %{buildroot}/etc/xroad/backup.d

ln -s /usr/share/xroad/bin/signer-console %{buildroot}/usr/bin/signer-console
ln -s /usr/share/xroad/jlib/signer/quarkus-run.jar %{buildroot}/usr/share/xroad/jlib/signer.jar
ln -s /usr/share/xroad/jlib/signer-console/quarkus-run.jar %{buildroot}/usr/share/xroad/jlib/signer-console.jar

cp -p %{_sourcedir}/signer/xroad-signer.service %{buildroot}%{_unitdir}
cp -p -r %{srcdir}/../../../../src/service/signer/signer-application/build/quarkus-app/* %{buildroot}/usr/share/xroad/jlib/signer/
cp -p -r %{srcdir}/../../../../src/service/signer/signer-cli/build/quarkus-app/* %{buildroot}/usr/share/xroad/jlib/signer-console/

#Copy arch specific libs
%ifarch x86_64
cp -p %{srcdir}/../../../../src/libs/pkcs11wrapper/amd64/libpkcs11wrapper.so %{buildroot}/usr/share/xroad/lib/
%endif

%ifarch aarch64
cp -p %{srcdir}/../../../../src/libs/pkcs11wrapper/arm64/libpkcs11wrapper.so %{buildroot}/usr/share/xroad/lib/
%endif

%clean
rm -rf %{buildroot}

%files
%defattr(0640,xroad,xroad,0751)
%dir /etc/xroad
%dir /etc/xroad/ssl
%attr(0750,xroad,xroad) %dir /etc/xroad/signer
%dir /etc/xroad/services
%dir /etc/xroad/conf.d
%config /etc/xroad/services/signer.conf
%config /etc/xroad/services/signer-console.conf
%attr(0440,xroad,xroad) %config /etc/xroad/backup.d/??_xroad-signer

%defattr(-,root,root,-)
/usr/bin/signer-console
/usr/share/xroad/bin/signer-console
/usr/share/xroad/jlib/signer.jar
/usr/share/xroad/jlib/signer-console.jar
/usr/share/xroad/jlib/signer/
/usr/share/xroad/jlib/signer-console/
/usr/share/xroad/lib/libpkcs11wrapper.so
%attr(754,root,xroad) /usr/share/xroad/bin/xroad-signer
%attr(644,root,root) %{_unitdir}/xroad-signer.service

%pre -p /bin/bash
%upgrade_check

mkdir -p %{_localstatedir}/lib/rpm-state/%{name}
if systemctl is-active %{name} &> /dev/null; then
  touch "%{_localstatedir}/lib/rpm-state/%{name}/active"
fi

%verifyscript

%post
umask 027

# nicer log directory permissions
mkdir -p -m1770 /var/log/xroad
chmod 1770 /var/log/xroad
chown xroad:adm /var/log/xroad

#tmp folder
mkdir -p /var/tmp/xroad
chmod 1750 /var/tmp/xroad
chown xroad:xroad /var/tmp/xroad

chown -R xroad:xroad /etc/xroad/services/* /etc/xroad/conf.d/*
chmod -R o=rwX,g=rX,o= /etc/xroad/services/* /etc/xroad/conf.d/*


%systemd_post xroad-signer.service

%preun
%systemd_preun xroad-signer.service

%postun
%systemd_postun_with_restart xroad-signer.service

%changelog
