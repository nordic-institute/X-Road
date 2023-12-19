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
mkdir -p %{buildroot}/usr/share/xroad/lib
mkdir -p %{buildroot}/etc/xroad
mkdir -p %{buildroot}/etc/xroad/services
mkdir -p %{buildroot}/etc/xroad/conf.d/addons
mkdir -p %{buildroot}/etc/xroad/ssl
mkdir -p %{buildroot}/etc/xroad/signer
mkdir -p %{buildroot}/var/lib/xroad/backup
mkdir -p %{buildroot}/etc/xroad/backup.d

ln -s /usr/share/xroad/bin/signer-console %{buildroot}/usr/bin/signer-console
ln -s /usr/share/xroad/jlib/signer-1.0.jar %{buildroot}/usr/share/xroad/jlib/signer.jar
ln -s /usr/share/xroad/jlib/signer-console-1.0.jar %{buildroot}/usr/share/xroad/jlib/signer-console.jar

cp -p %{_sourcedir}/signer/xroad-signer.service %{buildroot}%{_unitdir}
cp -p %{srcdir}/default-configuration/signer.ini %{buildroot}/etc/xroad/conf.d/
cp -p %{srcdir}/default-configuration/devices.ini %{buildroot}/etc/xroad/
cp -p %{srcdir}/default-configuration/signer-logback.xml %{buildroot}/etc/xroad/conf.d/
cp -p %{srcdir}/default-configuration/signer-console-logback.xml %{buildroot}/etc/xroad/conf.d/
cp -p %{srcdir}/../../../signer/build/libs/signer-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{srcdir}/../../../signer-console/build/libs/signer-console-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{srcdir}/../../../libs/libpkcs11wrapper.so %{buildroot}/usr/share/xroad/lib/
cp -p %{srcdir}/../../../lib/libpasswordstore.so %{buildroot}/usr/share/xroad/lib/

%clean
rm -rf %{buildroot}

%files
%defattr(0640,xroad,xroad,0751)
%dir /etc/xroad
%dir /etc/xroad/ssl
%attr(0750,xroad,xroad) %dir /etc/xroad/signer
%dir /etc/xroad/services
%dir /etc/xroad/conf.d
%dir /etc/xroad/conf.d/addons
%dir /var/lib/xroad
%dir /var/lib/xroad/backup
%config /etc/xroad/devices.ini
%config /etc/xroad/services/signer.conf
%config /etc/xroad/services/signer-console.conf
%config /etc/xroad/conf.d/signer.ini
%config /etc/xroad/conf.d/signer-logback.xml
%config /etc/xroad/conf.d/signer-console-logback.xml
%attr(0440,xroad,xroad) %config /etc/xroad/backup.d/??_xroad-signer

%defattr(-,root,root,-)
/usr/bin/signer-console
/usr/share/xroad/jlib/signer.jar
/usr/share/xroad/bin/signer-console
/usr/share/xroad/jlib/signer-*.jar
/usr/share/xroad/lib/libpasswordstore.so
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

# ensure home directory ownership
mkdir -p /var/lib/xroad/backup
su - xroad -c "test -O /var/lib/xroad && test -G /var/lib/xroad" || chown xroad:xroad /var/lib/xroad
chown xroad:xroad /var/lib/xroad/backup
chmod 0775 /var/lib/xroad

# nicer log directory permissions
mkdir -p -m1770 /var/log/xroad
chmod 1770 /var/log/xroad
chown xroad:adm /var/log/xroad

#tmp folder
mkdir -p /var/tmp/xroad
chmod 1750 /var/tmp/xroad
chown xroad:xroad /var/tmp/xroad

#local overrides
test -f /etc/xroad/services/local.conf || touch /etc/xroad/services/local.conf
test -f /etc/xroad/conf.d/local.ini || touch /etc/xroad/conf.d/local.ini

chown -R xroad:xroad /etc/xroad/services/* /etc/xroad/conf.d/*
chmod -R o=rwX,g=rX,o= /etc/xroad/services/* /etc/xroad/conf.d/*

# replace signer configuration property csr-signature-algorithm with csr-signature-digest-algorithm
local_ini=/etc/xroad/conf.d/local.ini
if csr_signature_algorithm=`crudini --get ${local_ini} signer csr-signature-algorithm 2>/dev/null`
then
    crudini --del ${local_ini} signer csr-signature-algorithm
    case "$csr_signature_algorithm" in
        SHA512*) crudini --set ${local_ini} signer csr-signature-digest-algorithm SHA-512;;
        SHA384*) crudini --set ${local_ini} signer csr-signature-digest-algorithm SHA-384;;
        SHA256*) crudini --set ${local_ini} signer csr-signature-digest-algorithm SHA-256;;
        SHA1*) crudini --set ${local_ini} signer csr-signature-digest-algorithm SHA-1;;
    esac
fi

# remove default-signature-algorithm
crudini --del ${local_ini} common default-signature-algorithm 2>/dev/null || :

# migrate keys to a new directory
signer_folder=/etc/xroad/signer
if [ ! -d ${signer_folder}/softtoken ]; then
    mkdir -p -m 0750 ${signer_folder}/softtoken.tmp
    test -f ${signer_folder}/.softtoken.p12 && cp -a ${signer_folder}/.softtoken.p12 ${signer_folder}/softtoken.tmp/.softtoken.p12
    ls ${signer_folder}/*.p12 > /dev/null 2>&1 && cp -a ${signer_folder}/*.p12 ${signer_folder}/softtoken.tmp/
    mv ${signer_folder}/softtoken.tmp ${signer_folder}/softtoken
    chown -R xroad:xroad ${signer_folder}/softtoken
    test -f ${signer_folder}/.softtoken.p12 && rm ${signer_folder}/.softtoken.p12
    ls ${signer_folder}/*.p12 > /dev/null 2>&1 && rm ${signer_folder}/*.p12
fi

%systemd_post xroad-signer.service

%preun
%systemd_preun xroad-signer.service

%postun
%systemd_postun_with_restart xroad-signer.service

%changelog
