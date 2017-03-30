# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-proxy
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road security server
Group:              Applications/Internet
License:            MIT
BuildRequires:      systemd
Requires(post):     systemd
Requires(preun):    systemd
Requires(postun):   systemd
Requires:           net-tools, policycoreutils-python, tar
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
mkdir -p %{buildroot}/usr/share/doc/%{name}

cp -p %{_sourcedir}/proxy/xroad-proxy %{buildroot}/usr/share/xroad/bin/
cp -p %{_sourcedir}/proxy/xroad-proxy-setup.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{_sourcedir}/proxy/xroad-initdb.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{_sourcedir}/proxy/xroad-proxy-port-redirect.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{_sourcedir}/proxy/xroad-add-admin-user.sh %{buildroot}/usr/share/xroad/bin/
cp -p %{_sourcedir}/proxy/xroad.pam %{buildroot}/etc/pam.d/xroad
cp -p %{_sourcedir}/proxy/xroad-*.service %{buildroot}%{_unitdir}
cp -p %{src}/../../proxy-ui/build/libs/proxy-ui.war %{buildroot}/usr/share/xroad/jlib/webapps/
cp -p %{src}/../../proxy/build/libs/proxy-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{src}/../default-configuration/proxy-rhel.ini %{buildroot}/etc/xroad/conf.d/proxy.ini
cp -p %{src}/../default-configuration/proxy-ui.ini %{buildroot}/etc/xroad/conf.d
cp -p %{src}/../default-configuration/proxy-logback.xml %{buildroot}/etc/xroad/conf.d
cp -p %{src}/../default-configuration/proxy-ui-jetty-logback-context-name.xml %{buildroot}/etc/xroad/conf.d
cp -p %{src}/../default-configuration/rsyslog.d/* %{buildroot}/etc/rsyslog.d/
cp -p %{src}/debian/xroad-proxy.logrotate %{buildroot}/etc/logrotate.d/xroad-proxy
cp -p %{src}/debian/trusty/proxy_restore_db.sh %{buildroot}/usr/share/xroad/scripts/restore_db.sh
cp -p %{src}/../../LICENSE.txt %{buildroot}/usr/share/doc/%{name}/LICENSE.txt
cp -p %{src}/../../securityserver-LICENSE.info %{buildroot}/usr/share/doc/%{name}/securityserver-LICENSE.info

ln -s /usr/share/xroad/jlib/proxy-1.0.jar %{buildroot}/usr/share/xroad/jlib/proxy.jar
ln -s /etc/xroad/conf.d/proxy-ui-jetty-logback-context-name.xml %{buildroot}/etc/xroad/conf.d/jetty-logback-context-name.xml
ln -s /usr/share/xroad/bin/xroad-add-admin-user.sh %{buildroot}/usr/bin/xroad-add-admin-user

%clean
rm -rf %{buildroot}

%files
%defattr(-,xroad,xroad,-)
%config /etc/xroad/services/proxy.conf
%config /etc/xroad/conf.d/proxy.ini
%config /etc/xroad/conf.d/proxy-ui.ini
%config /etc/xroad/conf.d/proxy-logback.xml
%config /etc/xroad/conf.d/jetty-logback-context-name.xml
%config /etc/xroad/conf.d/proxy-ui-jetty-logback-context-name.xml
%config /etc/xroad/jetty/clientproxy.xml
%config /etc/xroad/jetty/contexts-admin/proxy-ui.xml
%config /etc/xroad/jetty/serverproxy.xml
%config /etc/xroad/jetty/ocsp-responder.xml
%config /etc/xroad/services/jetty.conf
%config(noreplace) /etc/pam.d/xroad

%attr(644,root,root) %{_unitdir}/xroad-proxy.service

%config %attr(644,root,root) /etc/logrotate.d/xroad-proxy
%config %attr(644,root,root) /etc/rsyslog.d/40-xroad.conf
%config %attr(644,root,root) /etc/rsyslog.d/90-udp.conf
%config %attr(644,root,root) /etc/sudoers.d/xroad-proxy

%attr(540,xroad,xroad) /usr/share/xroad/bin/xroad-proxy
%attr(540,root,xroad) /usr/share/xroad/scripts/xroad-proxy-setup.sh
%attr(540,root,xroad) /usr/share/xroad/scripts/xroad-initdb.sh
%attr(540,root,xroad) /usr/share/xroad/scripts/xroad-proxy-port-redirect.sh
%attr(544,root,xroad) /usr/share/xroad/bin/xroad-add-admin-user.sh

/usr/bin/xroad-add-admin-user
/usr/lib/systemd/system/xroad-async.service
/usr/share/xroad/db/serverconf-changelog.xml
/usr/share/xroad/db/serverconf
/usr/share/xroad/db/backup_and_remove_non-member_permissions.sh
/usr/share/xroad/jlib/proxy*.jar
/usr/share/xroad/jlib/webapps/proxy-ui.war
/usr/share/xroad/scripts/backup_db.sh
/usr/share/xroad/scripts/restore_db.sh
/usr/share/xroad/scripts/verify_internal_configuration.sh
/usr/share/xroad/scripts/backup_xroad_proxy_configuration.sh
/usr/share/xroad/scripts/restore_xroad_proxy_configuration.sh
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/securityserver-LICENSE.info

%pre
if [ $1 -gt 1 ] ; then
    # upgrade
    # remove the previous port forwarding rules (if any)
    if [ -e /etc/sysconfig/xroad-proxy ]; then
        source /etc/sysconfig/xroad-proxy
    fi
    if [ -x /usr/share/xroad/scripts/xroad-proxy-port-redirect.sh ]; then
        /usr/share/xroad/scripts/xroad-proxy-port-redirect.sh disable
    fi

    mkdir -p %{_localstatedir}/lib/rpm-state/%{name}
    rpm -q xroad-proxy --queryformat="%%{version}" &> %{_localstatedir}/lib/rpm-state/%{name}/prev-version

fi

%post
%systemd_post xroad-proxy.service
%systemd_post xroad-confclient.service

if [ $1 -eq 1 ] ; then
    # Initial installation
    /usr/share/xroad/scripts/xroad-initdb.sh
    if ! grep -qs DISABLE_PORT_REDIRECT /etc/sysconfig/xroad-proxy; then
    cat <<"EOF" >>/etc/sysconfig/xroad-proxy
# Setting DISABLE_PORT_REDIRECT to false enables iptables port redirection (default: disabled)
# DISABLE_PORT_REDIRECT=true
EOF
    fi
fi

if [ $1 -gt 1 ] ; then
    # upgrade
    if [ ! -e /etc/sysconfig/xroad-proxy ]; then
        echo 'DISABLE_PORT_REDIRECT=false' >>/etc/sysconfig/xroad-proxy
    fi
fi

sh /usr/share/xroad/scripts/xroad-proxy-setup.sh >/var/log/xroad/proxy-install.log

if [ $1 -gt 1 ]; then
    # upgrade
    if grep -q "^6\.7\." %{_localstatedir}/lib/rpm-state/%{name}/prev-version; then
        # 6.7.x -> 6.8 specific migration
        bash /usr/share/xroad/db/backup_and_remove_non-member_permissions.sh >>/var/log/xroad/proxy-install.log
    fi
    rm -rf %{_localstatedir}/lib/rpm-state/%{name}
fi

%preun
%systemd_preun xroad-proxy.service
%systemd_preun xroad-confclient.service

%postun
%systemd_postun_with_restart xroad-proxy.service
%systemd_postun_with_restart xroad-confclient.service
%systemd_postun_with_restart xroad-jetty9.service

%changelog

