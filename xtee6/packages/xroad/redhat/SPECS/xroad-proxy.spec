# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-proxy
Version:            6.7
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road security server
Group:              Applications/Internet
License:            Proprietary
BuildRequires:      systemd
Requires(post):     systemd
Requires(preun):    systemd
Requires(postun):   systemd
Requires:           net-tools, policycoreutils-python
Requires:           xroad-common >= %version, xroad-jetty9 >= %version, rsyslog, postgresql-server, postgresql-contrib

%define src %{_topdir}/..

%description
X-Road security server programs and utilities

%prep
rm -rf proxy
cp -a %{src}/proxy .

%build

%install
cd proxy
cp -a * %{buildroot}

mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}/usr/share/xroad/jlib
mkdir -p %{buildroot}/usr/share/xroad/scripts
mkdir -p %{buildroot}/etc/xroad
mkdir -p %{buildroot}/etc/xroad/conf.d
mkdir -p %{buildroot}/etc/rsyslog.d
mkdir -p %{buildroot}/etc/pam.d
mkdir -p %{buildroot}/usr/share/xroad/jlib/webapps
mkdir -p %{buildroot}/usr/share/xroad/bin
mkdir -p %{buildroot}/etc/logrotate.d

cp -p %{_sourcedir}/proxy/xroad-{proxy,async,confclient} %{buildroot}/usr/share/xroad/bin/
cp -p %{_sourcedir}/proxy/xroad-proxy-setup.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{_sourcedir}/proxy/xroad-initdb.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{_sourcedir}/proxy/xroad-proxy-port-redirect.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{_sourcedir}/proxy/xroad-add-admin-user.sh %{buildroot}/usr/share/xroad/bin/
cp -p %{_sourcedir}/proxy/xroad.pam %{buildroot}/etc/pam.d/xroad
cp -p %{_sourcedir}/proxy/xroad-*.service %{buildroot}%{_unitdir}
cp -p %{src}/../../proxy-ui/build/libs/proxy-ui.war %{buildroot}/usr/share/xroad/jlib/webapps/
cp -p %{src}/../../proxy/build/libs/proxy-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{src}/../../async-sender/build/libs/async-sender-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{src}/../default-configuration/proxy-rhel.ini %{buildroot}/etc/xroad/conf.d/proxy.ini
cp -p %{src}/../default-configuration/proxy-ui.ini %{buildroot}/etc/xroad/conf.d
cp -p %{src}/../default-configuration/proxy-logback.xml %{buildroot}/etc/xroad/conf.d
cp -p %{src}/../default-configuration/proxy-ui-logback.xml %{buildroot}/etc/xroad/conf.d
cp -p %{src}/../default-configuration/proxy-ui-jetty-logback-context-name.xml %{buildroot}/etc/xroad/conf.d
cp -p %{src}/../default-configuration/async-sender-logback.xml %{buildroot}/etc/xroad/conf.d
cp -p %{src}/../default-configuration/rsyslog.d/* %{buildroot}/etc/rsyslog.d/
cp -p %{src}/debian/xroad-proxy.logrotate %{buildroot}/etc/logrotate.d/xroad-proxy
cp -p %{src}/debian/trusty/proxy_restore_db.sh %{buildroot}/usr/share/xroad/scripts/restore_db.sh

ln -s /usr/share/xroad/jlib/proxy-1.0.jar %{buildroot}/usr/share/xroad/jlib/proxy.jar
ln -s /usr/share/xroad/jlib/async-sender-1.0.jar %{buildroot}/usr/share/xroad/jlib/async-sender.jar
ln -s /etc/xroad/conf.d/proxy-ui-logback.xml %{buildroot}/etc/xroad/conf.d/jetty-ui-logback.xml
ln -s /etc/xroad/conf.d/proxy-ui-jetty-logback-context-name.xml %{buildroot}/etc/xroad/conf.d/jetty-logback-context-name.xml
ln -s /usr/share/xroad/bin/xroad-add-admin-user.sh %{buildroot}/usr/bin/xroad-add-admin-user

%clean
rm -rf %{buildroot}

%files
%defattr(-,xroad,xroad,-)
%config /etc/xroad/services/confclient.conf
%config /etc/xroad/services/proxy.conf
%config /etc/xroad/services/async-sender.conf
%config /etc/xroad/conf.d/proxy.ini
%config /etc/xroad/conf.d/proxy-ui.ini
%config /etc/xroad/conf.d/proxy-logback.xml
%config /etc/xroad/conf.d/proxy-ui-logback.xml
%config /etc/xroad/conf.d/async-sender-logback.xml
%config /etc/xroad/conf.d/jetty-logback-context-name.xml
%config /etc/xroad/conf.d/jetty-ui-logback.xml
%config /etc/xroad/conf.d/proxy-ui-jetty-logback-context-name.xml
%config /etc/xroad/jetty/clientproxy.xml
%config /etc/xroad/jetty/contexts-admin/proxy-ui.xml
%config /etc/xroad/jetty/serverproxy.xml
%config /etc/xroad/services/jetty.conf
%config(noreplace) /etc/pam.d/xroad

%attr(644,root,root) %{_unitdir}/xroad-proxy.service
%attr(644,root,root) %{_unitdir}/xroad-async.service
%attr(644,root,root) %{_unitdir}/xroad-confclient.service

%config %attr(644,root,root) /etc/logrotate.d/xroad-proxy
%config %attr(644,root,root) /etc/rsyslog.d/40-xroad.conf
%config %attr(644,root,root) /etc/rsyslog.d/90-udp.conf
%config %attr(644,root,root) /etc/sudoers.d/xroad-proxy

%attr(540,xroad,xroad) /usr/share/xroad/bin/xroad-proxy
%attr(540,xroad,xroad) /usr/share/xroad/bin/xroad-async
%attr(540,xroad,xroad) /usr/share/xroad/bin/xroad-confclient
%attr(540,root,xroad) /usr/share/xroad/scripts/xroad-proxy-setup.sh
%attr(540,root,xroad) /usr/share/xroad/scripts/xroad-initdb.sh
%attr(540,root,xroad) /usr/share/xroad/scripts/xroad-proxy-port-redirect.sh
%attr(544,root,xroad) /usr/share/xroad/bin/xroad-add-admin-user.sh

/usr/bin/xroad-add-admin-user
/usr/share/xroad/db/liquibase
/usr/share/xroad/db/liquibase.jar
/usr/share/xroad/db/serverconf-changelog.xml
/usr/share/xroad/db/serverconf
/usr/share/xroad/jlib/async-sender*.jar
/usr/share/xroad/jlib/proxy*.jar
/usr/share/xroad/jlib/webapps/proxy-ui.war
/usr/share/xroad/scripts/backup_db.sh
/usr/share/xroad/scripts/restore_db.sh
/usr/share/xroad/scripts/verify_internal_configuration.sh

%pre

%post
%systemd_post xroad-proxy.service
%systemd_post xroad-async.service
%systemd_post xroad-confclient.service

if [ $1 -eq 1 ] ; then
# Initial installation
/usr/share/xroad/scripts/xroad-initdb.sh
fi
sh /usr/share/xroad/scripts/xroad-proxy-setup.sh

%preun
%systemd_preun xroad-proxy.service
%systemd_preun xroad-async.service
%systemd_preun xroad-confclient.service

%postun
%systemd_postun_with_restart xroad-proxy.service
%systemd_postun_with_restart xroad-async.service
%systemd_postun_with_restart xroad-confclient.service

%changelog

