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
Requires:           systemd, xroad-common >= %version
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
mkdir -p %{buildroot}%{_sysconfdir}/xroad/conf.d
mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}/usr/share/xroad/bin

cp -p %{src}/../../monitor/build/libs/monitor-1.0.jar %{buildroot}%{jlib}
cp -a %{src}/monitor/etc/* %{buildroot}%{_sysconfdir}
cp -p %{src}/monitor/systemd/%{name}.service %{buildroot}%{_unitdir}
cp -p %{src}/monitor/systemd/%{name}  %{buildroot}/usr/share/xroad/bin
cp -p %{src}/../default-configuration/monitor.ini %{buildroot}%{_sysconfdir}/xroad/conf.d/
cp -p %{src}/../default-configuration/monitor-logback.xml %{buildroot}%{_sysconfdir}/xroad/conf.d/
ln -s %{jlib}/monitor-1.0.jar %{buildroot}%{jlib}/monitor.jar

%clean
rm -rf %{buildroot}

%files
%defattr(-,xroad,xroad,-)
%config %{_sysconfdir}/xroad/services/monitor.conf
%config %{_sysconfdir}/xroad/conf.d/monitor.ini
%config %{_sysconfdir}/xroad/conf.d/monitor-logback.xml
%attr(644,root,root) %{_unitdir}/xroad-monitor.service
%{jlib}/monitor-1.0.jar
%{jlib}/monitor.jar
%attr(744,xroad,xroad) /usr/share/xroad/bin/%{name}

%post
%systemd_post xroad-monitor.service

%preun
%systemd_preun xroad-monitor.service

%postun
%systemd_postun_with_restart xroad-monitor.service

%changelog

