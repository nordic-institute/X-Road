%include %{_specdir}/common.inc
# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-monitor
Version:            %{xroad_version}
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road Monitoring
Group:              Applications/Internet
License:            MIT
Requires:           systemd, xroad-base = %version-%release
Requires(post):     systemd
Requires(preun):    systemd
Requires(postun):   systemd

%define src %{_topdir}/..
%define jlib /usr/share/xroad/jlib

%description
X-Road monitoring component

%prep

%build

%install
mkdir -p %{buildroot}%{jlib}
mkdir -p %{buildroot}%{_sysconfdir}
mkdir -p %{buildroot}%{_sysconfdir}/xroad/conf.d/addons
mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}/usr/share/xroad/bin
mkdir -p %{buildroot}/etc/xroad/backup.d

cp -p %{srcdir}/../../../monitor/build/libs/monitor-1.0.jar %{buildroot}%{jlib}
cp -a %{srcdir}/common/monitor/etc/* %{buildroot}%{_sysconfdir}
cp -p %{srcdir}/common/monitor/systemd/%{name}.service %{buildroot}%{_unitdir}
cp -p %{srcdir}/common/monitor/usr/share/xroad/bin/xroad-monitor %{buildroot}/usr/share/xroad/bin
cp -p %{srcdir}/default-configuration/addons/monitor.ini %{buildroot}%{_sysconfdir}/xroad/conf.d/addons
cp -p %{srcdir}/default-configuration/addons/monitor-logback.xml %{buildroot}%{_sysconfdir}/xroad/conf.d/addons
cp -p %{srcdir}/common/monitor/etc/xroad/backup.d/??_xroad-monitor %{buildroot}%{_sysconfdir}/xroad/backup.d/
ln -s %{jlib}/monitor-1.0.jar %{buildroot}%{jlib}/monitor.jar

%clean
rm -rf %{buildroot}

%files
%defattr(-,xroad,xroad,-)
%config %{_sysconfdir}/xroad/services/monitor.conf
%config %{_sysconfdir}/xroad/conf.d/addons/monitor.ini
%config %{_sysconfdir}/xroad/conf.d/addons/monitor-logback.xml
%attr(0440,xroad,xroad) %config %{_sysconfdir}/xroad/backup.d/??_xroad-monitor
%defattr(-,root,root,-)
%attr(644,root,root) %{_unitdir}/xroad-monitor.service
%{jlib}/monitor-1.0.jar
%{jlib}/monitor.jar
%attr(754,root,xroad) /usr/share/xroad/bin/%{name}

%pre -p /bin/bash
%upgrade_check

mkdir -p %{_localstatedir}/lib/rpm-state/%{name}
if systemctl is-active %{name} &> /dev/null; then
  touch "%{_localstatedir}/lib/rpm-state/%{name}/active"
fi

%post
%systemd_post xroad-monitor.service

%preun
%systemd_preun xroad-monitor.service

%postun
%systemd_postun_with_restart xroad-monitor.service

%changelog

