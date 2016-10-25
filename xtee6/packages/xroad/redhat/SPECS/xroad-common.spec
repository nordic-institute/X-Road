# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:       xroad-common
Version:    %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:    %{rel}%{?snapshot}%{?dist}
Summary:    X-Road shared components
Group:      Applications/Internet
License:    MIT
Requires(post): systemd
Requires(preun): systemd
Requires(postun): systemd
BuildRequires: systemd
Requires:  systemd
Requires:  postgresql-server, postgresql-contrib
Requires:  rlwrap, nginx >= 1.5.10, ntp, crudini
Requires:  jre-1.8.0-headless >= 1.8.0.51

%define src %{_topdir}/..

%description
X-Road shared components and utilities

%prep
rm -rf common
cp -a %{src}/common .
cd common
rm -rf etc/rcS.d
sed -i 's/JAVA_HOME=.*/JAVA_HOME=\/etc\/alternatives\/jre_1.8.0_openjdk/' etc/xroad/services/global.conf

%build

%install
cd common
cp -a * %{buildroot}

mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}/usr/share/xroad/jlib
mkdir -p %{buildroot}/usr/share/xroad/lib
mkdir -p %{buildroot}/etc/xroad
mkdir -p %{buildroot}/etc/xroad/services
mkdir -p %{buildroot}/etc/xroad/nginx
mkdir -p %{buildroot}/etc/xroad/conf.d
mkdir -p %{buildroot}/etc/nginx/conf.d
mkdir -p %{buildroot}/usr/share/doc/%{name}

ln -s /usr/share/xroad/bin/signer-console %{buildroot}/usr/bin/signer-console
ln -s /usr/share/xroad/jlib/signer-1.0.jar %{buildroot}/usr/share/xroad/jlib/signer.jar
ln -s /usr/share/xroad/jlib/signer-console-1.0.jar %{buildroot}/usr/share/xroad/jlib/signer-console.jar
ln -s /usr/share/xroad/jlib/configuration-client-1.0.jar %{buildroot}/usr/share/xroad/jlib/configuration-client.jar
ln -s /etc/xroad/nginx/default-xroad.conf %{buildroot}/etc/nginx/conf.d/default-xroad.conf
ln -s /etc/xroad/nginx/nginx-secure-addons.conf %{buildroot}/etc/nginx/conf.d/xroad-securing.conf

cp -p %{_sourcedir}/signer/xroad-signer %{buildroot}%{_bindir}
cp -p %{_sourcedir}/signer/xroad-signer.service %{buildroot}%{_unitdir}
cp -p %{src}/../default-configuration/common.ini %{buildroot}/etc/xroad/conf.d/
cp -p %{src}/../default-configuration/signer.ini %{buildroot}/etc/xroad/conf.d/
cp -p %{src}/../default-configuration/devices.ini %{buildroot}/etc/xroad/
cp -p %{src}/../default-configuration/signer-logback.xml %{buildroot}/etc/xroad/conf.d/
cp -p %{src}/../default-configuration/signer-console-logback.xml %{buildroot}/etc/xroad/conf.d/
cp -p %{src}/../../signer/build/libs/signer-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{src}/../../signer-console/build/libs/signer-console-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{src}/../../libs/libpkcs11wrapper.so %{buildroot}/usr/share/xroad/lib/
cp -p %{src}/../../lib/libpasswordstore.so %{buildroot}/usr/share/xroad/lib/
cp -p %{src}/../../configuration-client/build/libs/configuration-client-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{src}/../default-configuration/confclient-logback.xml %{buildroot}/etc/xroad/conf.d
cp -p %{src}/../../securityserver-LICENSE.txt %{buildroot}/usr/share/doc/%{name}/securityserver-LICENSE.txt
cp -p %{src}/../../securityserver-LICENSE.info %{buildroot}/usr/share/doc/%{name}/securityserver-LICENSE.info

%clean
rm -rf %{buildroot}

%files
%defattr(-,xroad,xroad,-)
%attr(0440,root,root) %config /etc/sudoers.d/xroad-restore
%config /etc/xroad/devices.ini
%config /etc/xroad/services/global.conf
%config /etc/xroad/services/signer.conf
%config /etc/xroad/services/signer-console.conf
%config /etc/xroad/nginx/default-xroad.conf
%config /etc/xroad/nginx/nginx-secure-addons.conf
%config /etc/xroad/conf.d/common.ini
%config /etc/xroad/conf.d/signer.ini
%config /etc/xroad/conf.d/signer-logback.xml
%config /etc/xroad/conf.d/signer-console-logback.xml
%config /etc/xroad/conf.d/confclient-logback.xml
%config /etc/xroad/ssl/openssl.cnf
%config /etc/xroad/ssl/rfc3526group15.pem
/etc/nginx/conf.d/xroad-securing.conf
/etc/nginx/conf.d/default-xroad.conf
/usr/bin/signer-console
/usr/share/xroad/jlib/configuration-client.jar
/usr/share/xroad/jlib/signer.jar
/usr/share/xroad/bin/signer-console
/usr/share/xroad/jlib/configuration-client-*.jar
/usr/share/xroad/jlib/signer-*.jar
/usr/share/xroad/lib/libpasswordstore.so
/usr/share/xroad/lib/libpkcs11wrapper.so
/usr/share/xroad/scripts/_backup_xroad.sh
/usr/share/xroad/scripts/generate_certificate.sh
/usr/share/xroad/scripts/_restore_xroad.sh
/usr/share/xroad/scripts/_backup_restore_common.sh
/usr/share/xroad/scripts/serverconf_migrations/add_acl.xsl
%attr(754,xroad,xroad) %{_bindir}/xroad-signer
%attr(664,root,root) %{_unitdir}/xroad-signer.service
%doc /usr/share/doc/%{name}/securityserver-LICENSE.txt
%doc /usr/share/doc/%{name}/securityserver-LICENSE.info

%pre
if ! getent passwd xroad > /dev/null; then
useradd --system --home /var/lib/xroad --no-create-home --shell /bin/bash --user-group --comment "X-Road system user" xroad
fi

%verifyscript
# check validity of xroad user and group
if [ "`id -u xroad`" -eq 0 ]; then
echo "The xroad system user must not have uid 0 (root). Please fix this and reinstall this package." >&2
exit 1
fi
if [ "`id -g xroad`" -eq 0 ]; then
echo "The xroad system user must not have root as primary group. Please fix this and reinstall this package." >&2
exit 1
fi

%post
# ensure home directory ownership
mkdir -p /var/lib/xroad/backup
su - xroad -c "test -O /var/lib/xroad && test -G /var/lib/xroad" || chown xroad:xroad /var/lib/xroad
chown xroad:xroad /var/lib/xroad/backup
chmod 0775 /var/lib/xroad

# nicer log directory permissions
mkdir -p -m1770 /var/log/xroad
chmod 1770 /var/log/xroad
chown xroad:adm /var/log/xroad

# test and fix config folder
su - xroad -c "test -O /etc/xroad && test -G /etc/xroad" || chown xroad:xroad /etc/xroad
chmod 0770 /etc/xroad

#tmp folder
mkdir -p /var/tmp/xroad
chmod 1770 /var/tmp/xroad
chown xroad:xroad /var/tmp/xroad

chmod 770 /etc/xroad/ssl
chown xroad:xroad /etc/xroad/ssl

# create socket directory
[ -d /var/run/xroad ] || install -d -m 2770 -o xroad -g xroad /var/run/xroad

#local overrides
test -f /etc/xroad/services/local.conf || touch /etc/xroad/services/local.conf
test -f /etc/xroad/conf.d/local.ini || touch /etc/xroad/conf.d/local.ini

test -d /etc/xroad/signer || mkdir -p -m0700 /etc/xroad/signer && chown xroad:xroad /etc/xroad/signer
test -d /etc/xroad/conf.d || mkdir -p /etc/xroad/conf.d && chown xroad:xroad /etc/xroad/conf.d
test -d /etc/xroad/ssl || mkdir /etc/xroad/ssl

#enable xroad services by default
echo 'enable xroad-*.service' > %{_presetdir}/90-xroad.preset
%systemd_post xroad-signer.service

%preun
%systemd_preun xroad-signer.service

%postun
%systemd_postun_with_restart xroad-signer.service

%changelog

