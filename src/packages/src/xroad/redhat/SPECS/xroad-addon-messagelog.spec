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
mkdir -p %{buildroot}/usr/share/xroad/jlib/addon/proxy/messagelog-archiver
mkdir -p %{buildroot}/usr/share/xroad/scripts
mkdir -p %{buildroot}/usr/share/xroad/bin
mkdir -p %{buildroot}/etc/xroad/conf.d/addons
mkdir -p %{buildroot}/etc/xroad/services
mkdir -p %{buildroot}/usr/share/xroad/db/messagelog
mkdir -p %{buildroot}/usr/share/doc/xroad-addon-messagelog/archive-server
mkdir -p %{buildroot}/usr/share/doc/xroad-addon-messagelog/archive-hashchain-verifier
mkdir -p %{buildroot}/usr/share/doc/%{name}
mkdir -p %{buildroot}%{_unitdir}

cp -p %{_sourcedir}/messagelog/xroad-addon-messagelog.service %{buildroot}%{_unitdir}
cp -p %{srcdir}/default-configuration/addons/message-log.ini %{buildroot}/etc/xroad/conf.d/addons/
cp -p %{srcdir}/common/addon/proxy/messagelog-changelog.xml %{buildroot}/usr/share/xroad/db/
cp -p %{srcdir}/common/addon/proxy/messagelog/* %{buildroot}/usr/share/xroad/db/messagelog
cp -p %{srcdir}/common/addon/proxy/setup_messagelog_db.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{srcdir}/common/addon/proxy/xroad-messagelog-archiver %{buildroot}/usr/share/xroad/bin/
cp -p %{srcdir}/common/addon/proxy/messagelog-archiver.conf %{buildroot}/etc/xroad/services/

cp -p -r %{srcdir}/../../../service/message-log-archiver/message-log-archiver-application/build/quarkus-app/* %{buildroot}/usr/share/xroad/jlib/addon/proxy/messagelog-archiver/
cp -p %{srcdir}/../../../service/message-log-archiver/message-log-archiver-application/scripts/archive-http-transporter.sh %{buildroot}/usr/share/xroad/scripts
cp -p %{srcdir}/../../../service/message-log-archiver/message-log-archiver-application/scripts/demo-upload.pl %{buildroot}/usr/share/doc/xroad-addon-messagelog/archive-server/
cp -p %{srcdir}/../../../addons/messagelog/messagelog-archive-verifier/build/libs/messagelog-archive-verifier.jar %{buildroot}/usr/share/doc/xroad-addon-messagelog/archive-hashchain-verifier/
cp -p %{srcdir}/../../../addons/messagelog/messagelog-archive-verifier/README.md %{buildroot}/usr/share/doc/xroad-addon-messagelog/archive-hashchain-verifier/
cp -p %{srcdir}/../../../tool/asic-verifier-cli/build/libs/asicverifier.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{srcdir}/../../../LICENSE.txt %{buildroot}/usr/share/doc/%{name}/
cp -p %{srcdir}/../../../3RD-PARTY-NOTICES.txt %{buildroot}/usr/share/doc/%{name}/
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/%{name}/

ln -s /usr/share/xroad/jlib/addon/proxy/messagelog-archiver/quarkus-run.jar %{buildroot}/usr/share/xroad/jlib/addon/proxy/messagelog-archiver.jar

%clean
rm -rf %{buildroot}

%files
%defattr(-,xroad,xroad,-)
%config /etc/xroad/conf.d/addons/message-log.ini
%config /etc/xroad/services/messagelog-archiver.conf
%defattr(-,root,root,-)
%{_unitdir}/%{name}.service
/usr/share/doc/xroad-addon-messagelog/archive-hashchain-verifier/README.md
/usr/share/doc/xroad-addon-messagelog/archive-hashchain-verifier/messagelog-archive-verifier.jar
/usr/share/doc/xroad-addon-messagelog/archive-server/demo-upload.pl
/usr/share/xroad/db/messagelog-changelog.xml
/usr/share/xroad/db/messagelog
/usr/share/xroad/jlib/addon/proxy/messagelog-archiver/
/usr/share/xroad/jlib/addon/proxy/messagelog-archiver.jar
/usr/share/xroad/scripts/archive-http-transporter.sh
%attr(540,root,xroad) /usr/share/xroad/scripts/setup_messagelog_db.sh
%attr(554,root,xroad) /usr/share/xroad/bin/xroad-messagelog-archiver
/usr/share/xroad/jlib/asicverifier.jar
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
%doc /usr/share/doc/%{name}/CHANGELOG.md

%pre -p /bin/bash
%upgrade_check

mkdir -p %{_localstatedir}/lib/rpm-state/%{name}
if systemctl is-active %{name} &> /dev/null; then
  touch "%{_localstatedir}/lib/rpm-state/%{name}/active"
fi

if [ "$1" -gt 1 ] ; then
  rpm -q %{name} --queryformat="%%{version}" &> "%{_localstatedir}/lib/rpm-state/%{name}/prev-version"
fi

%define manage_messagelog_activation()                                               \\\
    isMessagelogDisabled=\$(                                                         \\\
        [[ -e /etc/sysconfig/xroad-addon-messagelog ]]                               \\\
        && grep -qs "ENABLE_MESSAGELOG=false" /etc/sysconfig/xroad-addon-messagelog  \\\
        && echo 1 || echo 0                                                          \\\
    );                                                                               \\\
    if (( \$isMessagelogDisabled )); then                                            \\\
      rm -f /usr/share/xroad/jlib/addon/proxy/messagelog.conf;                       \\\
    else                                                                             \\\
      /usr/share/xroad/scripts/setup_messagelog_db.sh;                               \\\
    fi

%post -p /bin/bash
%systemd_post xroad-addon-messagelog.service

# RHEL7 java-21-* package makes java binaries available since %post scriptlet
%if 0%{?el7}
%manage_messagelog_activation
%endif

if [ "$1" -gt 1 ]; then
  prev_version=$(cat %{_localstatedir}/lib/rpm-state/%{name}/prev-version)

  if [[ $prev_version = 6.* || $prev_version = 7.0.0 || $prev_version = 7.0.1 ]]; then
    # Special handling for upgrade from 6.x (and a fix for 7.0)
    systemctl --quiet preset xroad-addon-messagelog.service &>/dev/null || :
  fi

  rm -f "%{_localstatedir}/lib/rpm-state/%{name}/prev-version" >/dev/null 2>&1 || :
fi

%preun
%systemd_preun xroad-addon-messagelog.service

%postun
if [ "$1" -eq 0 ]; then
  # addon removed
  rm -f /usr/share/xroad/jlib/addon/proxy/messagelog.conf
fi

%systemd_postun_with_restart xroad-proxy.service xroad-addon-messagelog.service

%posttrans -p /bin/bash
# RHEL8/9 java-21-* package makes java binaries available since %posttrans scriptlet
%if 0%{?el8} || 0%{?el9}
%manage_messagelog_activation
%endif

%changelog
