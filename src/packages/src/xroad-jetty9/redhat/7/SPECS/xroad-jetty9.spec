# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)
# disable useless debuginfo package
%define debug_package %{nil}

Name:		xroad-jetty9
Version:        %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:        %{rel}%{?snapshot}%{?dist}
Summary:        Jetty9 for X-Road purposes
Group:          Applications/Internet
Autoprov:       0
# install fails if config is not provided
Provides:       config(%{name}) = %{version}-%{release}
License:        ASL 2.0/EPL 1.0

BuildRequires:  systemd, curl
Requires(post): systemd
Requires(preun): systemd
Requires(postun): systemd
Requires:	systemd, xroad-base = %{version}-%{release}
Conflicts: 	xroad-center, xroad-confproxy

%description
Jetty9 modified for X-Road usage. Used by web services.

%install
mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}/usr/share/xroad/jetty9
mkdir -p %{buildroot}/var/log/xroad/jetty
mkdir -p %{buildroot}/usr/share/xroad/webapps
mkdir -p %{buildroot}/usr/share/xroad/jlib
mkdir -p %{buildroot}/etc/xroad/jetty/contexts-admin
mkdir -p %{buildroot}/usr/lib/tmpfiles.d

cp -aP %{jetty}/* %{buildroot}/usr/share/xroad/jetty9
ln -s /etc/xroad/jetty/jetty-login.conf %{buildroot}/usr/share/xroad/jetty9/etc/login.conf
ln -s /etc/xroad/jetty/xroad.mod %{buildroot}/usr/share/xroad/jetty9/modules/xroad.mod
ln -s /etc/xroad/jetty/start.ini %{buildroot}/usr/share/xroad/jetty9/start.ini
ln -s /etc/xroad/conf.d/jetty-logback.xml %{buildroot}/usr/share/xroad/jetty9/resources/logback.xml

cp -p %{srcdir}/../../../common-util/build/libs/common-util-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -aP %{srcdir}/generic/etc %{buildroot}
cp -aP %{srcdir}/generic/usr %{buildroot}

cp %{_sourcedir}/%{name}.service %{buildroot}%{_unitdir}
cp %{_sourcedir}/%{name}.conf %{buildroot}/usr/lib/tmpfiles.d/

%clean
rm -rf %{buildroot}

%files
%defattr(0640,xroad,xroad,0751)
%dir /etc/xroad/jetty
%dir /etc/xroad/jetty/contexts-admin
%config /etc/xroad/jetty/*
%config /etc/xroad/conf.d/jetty-logback.xml
%attr(664,root,root) /usr/lib/tmpfiles.d/%{name}.conf
/usr/share/xroad/jlib/common-util-1.0.jar
/usr/share/xroad/jetty9
%dir /var/log/xroad/jetty
%dir /usr/share/xroad/webapps
%attr(755,root,root) /usr/share/xroad/bin/%{name}
%attr(664,root,root) %{_unitdir}/%{name}.service

%post
%systemd_post xroad-jetty9.service

%preun
%systemd_preun xroad-jetty9.service

%postun
%systemd_postun_with_restart xroad-jetty9.service

%changelog

