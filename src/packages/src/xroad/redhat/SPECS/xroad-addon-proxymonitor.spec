%include %{_specdir}/common.inc
# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:       xroad-addon-proxymonitor
Version:    %{xroad_version}
Release:    %{rel}%{?snapshot}%{?dist}
Summary:    X-Road addon: proxy monitoring
Group:      Applications/Internet
License:    MIT
Requires:   xroad-proxy = %version-%release, xroad-monitor = %version-%release
Requires(post): systemd
Requires(preun): systemd
Requires(postun): systemd

%define src %{_topdir}/..

%description
Addon for proxy monitoring

%prep

%build

%install
mkdir -p %{buildroot}/usr/share/xroad/jlib/addon/proxy/
cp -a %{srcdir}/common/addon/proxy/proxymonitor-service.conf %{buildroot}/usr/share/xroad/jlib/addon/proxy/
cp -p %{srcdir}/../../../addons/proxymonitor/metaservice/build/libs/proxymonitor-metaservice-1.0.jar %{buildroot}/usr/share/xroad/jlib/addon/proxy/

%clean
rm -rf %{buildroot}

%files
%defattr(-,root,root,-)
/usr/share/xroad/jlib/addon/proxy/proxymonitor-metaservice-1.0.jar
/usr/share/xroad/jlib/addon/proxy/proxymonitor-service.conf

%pre -p /bin/bash
%upgrade_check

%postun
%systemd_postun_with_restart xroad-proxy.service

%changelog
