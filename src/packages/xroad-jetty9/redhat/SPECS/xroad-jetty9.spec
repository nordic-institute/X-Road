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
URL:            https://confluence.csc.fi/display/Palveluvayla
Source0:        %{jetty}
Source1:        https://raw.githubusercontent.com/jetty-project/logging-modules/master/logback/logging.mod
Source2:        %{name}
Source3:        %{name}.service
Source4:        %{name}.conf

BuildRequires:  systemd, curl
Requires(post): systemd
Requires(preun): systemd
Requires(postun): systemd
Requires:	systemd, xroad-common >= 6.5
Conflicts: 	xroad-center, xroad-confproxy

%description
Jetty9 modified for X-Road usage. Used by web services.

%prep
%setup -n %(basename -s .tar.gz %SOURCE0)

%build
cp %{SOURCE1} modules/logging.mod
sed -i'' 's/^resources/#resources/' modules/logging.mod
sed -i'' 's/^logs/#logs/' modules/logging.mod
mv start.ini start.ini.bak
java -jar start.jar --add-to-start=logging jetty.base=$(pwd)

%install
ln -s /etc/xroad/jetty/jetty-login.conf etc/login.conf
ln -s /etc/xroad/jetty/xroad.mod modules/xroad.mod
rm -f start.ini
ln -s /etc/xroad/jetty/start.ini start.ini
mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}/usr/share/xroad/jetty9
mkdir -p %{buildroot}/var/log/xroad/jetty
mkdir -p %{buildroot}/usr/share/xroad/webapps
cp -aP * %{buildroot}/usr/share/xroad/jetty9
cp -aP %{_topdir}/../etc %{buildroot}/etc
cp %{SOURCE2} %{buildroot}%{_bindir}
cp %{SOURCE3} %{buildroot}%{_unitdir}
mkdir -p %{buildroot}/usr/lib/tmpfiles.d
cp %{SOURCE4} %{buildroot}/usr/lib/tmpfiles.d/

%clean
rm -rf %{buildroot}

%files
%defattr(-,xroad,xroad,-)
%config /etc/xroad/jetty
%config /etc/xroad/conf.d/jetty-logback.xml
%attr(664,root,root) /usr/lib/tmpfiles.d/%{name}.conf

/usr/share/xroad/jetty9
%dir /var/log/xroad/jetty
%dir /usr/share/xroad/webapps
%attr(754,xroad,xroad) %{_bindir}/%{name}
%attr(664,root,root) %{_unitdir}/%{name}.service

%post
%systemd_post xroad-jetty9.service

%preun
%systemd_preun xroad-jetty9.service

%postun
%systemd_postun_with_restart xroad-jetty9.service

%changelog

