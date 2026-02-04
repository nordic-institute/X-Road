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
Requires:           (xroad-secret-store-local = %version-%release or xroad-secret-store-remote = %version-%release)
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
mkdir -p %{buildroot}%{jlib}/monitor
mkdir -p %{buildroot}%{_sysconfdir}
mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}/usr/share/xroad/bin
mkdir -p %{buildroot}/etc/xroad/backup.d

cp -p -r %{srcdir}/../../../../src/service/monitor/monitor-application/build/quarkus-app/* %{buildroot}%{jlib}/monitor/
cp -a %{srcdir}/common/monitor/etc/* %{buildroot}%{_sysconfdir}
cp -p %{srcdir}/common/monitor/systemd/%{name}.service %{buildroot}%{_unitdir}
cp -p %{srcdir}/common/monitor/usr/share/xroad/bin/xroad-monitor %{buildroot}/usr/share/xroad/bin
cp -p %{srcdir}/common/monitor/etc/xroad/backup.d/??_xroad-monitor %{buildroot}%{_sysconfdir}/xroad/backup.d/
ln -s %{jlib}/monitor/quarkus-run.jar %{buildroot}%{jlib}/monitor.jar

%clean
rm -rf %{buildroot}

%files
%defattr(-,xroad,xroad,-)
%config %{_sysconfdir}/xroad/services/monitor.conf
%attr(0440,xroad,xroad) %config %{_sysconfdir}/xroad/backup.d/??_xroad-monitor
%defattr(-,root,root,-)
%attr(644,root,root) %{_unitdir}/xroad-monitor.service
%{jlib}/monitor/
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

