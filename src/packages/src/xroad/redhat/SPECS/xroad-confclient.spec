%include %{_specdir}/common.inc
# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:       xroad-confclient
Version:    %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:    %{rel}%{?snapshot}%{?dist}
Summary:    X-Road configuration client
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
X-Road configuration client

%prep
rm -rf confclient
cp -a %{srcdir}/common/confclient .
cd confclient

%build

%install
cd confclient
cp -a * %{buildroot}

mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}/usr/share/xroad/jlib
mkdir -p %{buildroot}/usr/share/xroad/lib
mkdir -p %{buildroot}/etc/xroad
mkdir -p %{buildroot}/etc/xroad/services
mkdir -p %{buildroot}/etc/xroad/conf.d/addons
mkdir -p %{buildroot}/usr/share/doc/%{name}
mkdir -p %{buildroot}/var/lib/xroad/backup
mkdir -p %{buildroot}/etc/xroad/backup.d
mkdir -p %{buildroot}/usr/share/xroad/bin

ln -s /usr/share/xroad/jlib/configuration-client-1.0.jar %{buildroot}/usr/share/xroad/jlib/configuration-client.jar

cp -p %{_sourcedir}/confclient/xroad-confclient.service %{buildroot}%{_unitdir}
cp -p %{srcdir}/../../../configuration-client/build/libs/configuration-client-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{srcdir}/default-configuration/confclient-logback.xml %{buildroot}/etc/xroad/conf.d
cp -p %{srcdir}/default-configuration/confclient-logback-service.xml %{buildroot}/etc/xroad/conf.d
cp -p %{srcdir}/common/confclient/etc/xroad/backup.d/??_xroad-confclient %{buildroot}/etc/xroad/backup.d/

%clean
rm -rf %{buildroot}

%files
%defattr(0640,xroad,xroad,0751)
%dir /etc/xroad
%dir /etc/xroad/services
%dir /etc/xroad/conf.d
%dir /etc/xroad/conf.d/addons
%dir /var/lib/xroad
%dir /var/lib/xroad/backup
%config /etc/xroad/services/confclient.conf
%config /etc/xroad/conf.d/confclient-logback.xml
%config /etc/xroad/conf.d/confclient-logback-service.xml
%attr(0440,xroad,xroad) %config /etc/xroad/backup.d/??_xroad-confclient

%defattr(-,root,root,-)
/usr/share/xroad/jlib/configuration-client.jar
/usr/share/xroad/jlib/configuration-client-*.jar
%attr(550,root,xroad) /usr/share/xroad/bin/xroad-confclient
%attr(644,root,root) %{_unitdir}/xroad-confclient.service

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

chown -R xroad:xroad /etc/xroad/services/* /etc/xroad/conf.d/*
chmod -R o=rwX,g=rX,o= /etc/xroad/services/* /etc/xroad/conf.d/*

if [ $1 -gt 1 ] ; then
    # upgrade
    if [ -e /etc/xroad/globalconf/files ]; then
        rm -f /etc/xroad/globalconf/files
    fi
fi

%systemd_post xroad-confclient.service

%preun
%systemd_preun xroad-confclient.service

%postun
%systemd_postun_with_restart xroad-confclient.service

%changelog
