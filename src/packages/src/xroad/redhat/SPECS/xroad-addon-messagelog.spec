%include %{_specdir}/common.inc
# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:       xroad-addon-messagelog
Version:    %{xroad_version}
Release:    %{rel}%{?snapshot}%{?dist}
Summary:    X-Road AddOn: messagelog
Group:      Applications/Internet
License:    MIT
Requires:   xroad-proxy = %version-%release

%define src %{_topdir}/..

%description
AddOn for secure message log

%prep

%build

%install
mkdir -p %{buildroot}/usr/share/xroad/jlib/addon/proxy/
mkdir -p %{buildroot}/usr/share/xroad/scripts
mkdir -p %{buildroot}/usr/share/xroad/bin
mkdir -p %{buildroot}/etc/xroad/conf.d/addons
mkdir -p %{buildroot}/etc/xroad/services
mkdir -p %{buildroot}/usr/share/xroad/db/messagelog
mkdir -p %{buildroot}/usr/share/doc/xroad-addon-messagelog/archive-server
mkdir -p %{buildroot}/usr/share/doc/xroad-addon-messagelog/archive-hashchain-verifier
mkdir -p %{buildroot}/usr/share/doc/%{name}
mkdir -p %{buildroot}%{_unitdir}

cp -p %{srcdir}/common/addon/proxy/messagelog.conf.default %{buildroot}/usr/share/xroad/jlib/addon/proxy/
cp -p %{_sourcedir}/messagelog/xroad-addon-messagelog.service %{buildroot}%{_unitdir}
cp -p %{srcdir}/../../../addons/messagelog/messagelog-addon/build/libs/messagelog-addon.jar %{buildroot}/usr/share/xroad/jlib/addon/proxy/
cp -p %{srcdir}/default-configuration/addons/message-log.ini %{buildroot}/etc/xroad/conf.d/addons/
cp -p %{srcdir}/default-configuration/addons/messagelog-archiver-logback.xml %{buildroot}/etc/xroad/conf.d/addons/
cp -p %{srcdir}/common/addon/proxy/messagelog-changelog.xml %{buildroot}/usr/share/xroad/db/
cp -p %{srcdir}/common/addon/proxy/messagelog/* %{buildroot}/usr/share/xroad/db/messagelog
cp -p %{srcdir}/common/addon/proxy/setup_messagelog_db.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{srcdir}/common/addon/proxy/xroad-messagelog-archiver %{buildroot}/usr/share/xroad/bin/
cp -p %{srcdir}/common/addon/proxy/messagelog-archiver.conf %{buildroot}/etc/xroad/services/

cp -p %{srcdir}/../../../addons/messagelog/messagelog-archiver/build/libs/messagelog-archiver.jar %{buildroot}/usr/share/xroad/jlib/addon/proxy/
cp -p %{srcdir}/../../../addons/messagelog/messagelog-archiver/scripts/archive-http-transporter.sh %{buildroot}/usr/share/xroad/scripts
cp -p %{srcdir}/../../../addons/messagelog/messagelog-archiver/scripts/demo-upload.pl %{buildroot}/usr/share/doc/xroad-addon-messagelog/archive-server/
cp -p %{srcdir}/../../../../doc/archive-hashchain-verifier.rb %{buildroot}/usr/share/doc/xroad-addon-messagelog/archive-hashchain-verifier/
cp -p %{srcdir}/../../../../doc/archive-hashchain-verifier.README %{buildroot}/usr/share/doc/xroad-addon-messagelog/archive-hashchain-verifier/README
cp -p %{srcdir}/../../../asicverifier/build/libs/asicverifier.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{srcdir}/../../../LICENSE.txt %{buildroot}/usr/share/doc/%{name}/
cp -p %{srcdir}/../../../3RD-PARTY-NOTICES.txt %{buildroot}/usr/share/doc/%{name}/
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/%{name}/

%clean
rm -rf %{buildroot}

%files
%defattr(-,xroad,xroad,-)
%config /etc/xroad/conf.d/addons/message-log.ini
%config /etc/xroad/conf.d/addons/messagelog-archiver-logback.xml
%config /etc/xroad/services/messagelog-archiver.conf
%defattr(-,root,root,-)
%{_unitdir}/%{name}.service
/usr/share/doc/xroad-addon-messagelog/archive-hashchain-verifier/README
/usr/share/doc/xroad-addon-messagelog/archive-hashchain-verifier/archive-hashchain-verifier.rb
/usr/share/doc/xroad-addon-messagelog/archive-server/demo-upload.pl
/usr/share/xroad/db/messagelog-changelog.xml
/usr/share/xroad/db/messagelog
/usr/share/xroad/jlib/addon/proxy/messagelog-addon.jar
/usr/share/xroad/jlib/addon/proxy/messagelog-archiver.jar
/usr/share/xroad/jlib/addon/proxy/messagelog.conf.default
/usr/share/xroad/scripts/archive-http-transporter.sh
%attr(540,root,xroad) /usr/share/xroad/scripts/setup_messagelog_db.sh
%attr(554,root,xroad) /usr/share/xroad/bin/xroad-messagelog-archiver
/usr/share/xroad/jlib/asicverifier.jar
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
%doc /usr/share/doc/%{name}/CHANGELOG.md

%pre -p /bin/bash
%upgrade_check

%post
if [ -e /etc/sysconfig/xroad-addon-messagelog ] && grep -qs "ENABLE_MESSAGELOG=false" /etc/sysconfig/xroad-addon-messagelog; then
    rm -f /usr/share/xroad/jlib/addon/proxy/messagelog.conf
else
    ln -s /usr/share/xroad/jlib/addon/proxy/messagelog.conf.default /usr/share/xroad/jlib/addon/proxy/messagelog.conf
    /usr/share/xroad/scripts/setup_messagelog_db.sh
fi
%systemd_post xroad-addon-messagelog.service

%postun
%systemd_postun_with_restart xroad-proxy.service
%systemd_postun_with_restart xroad-addon-messagelog.service

%changelog
