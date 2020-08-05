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
Requires(post):     /usr/sbin/semanage, /usr/sbin/setsebool
Requires(preun):    systemd
Requires(postun):   systemd
Requires:           net-tools, tar
Requires:           xroad-base = %version-%release, rsyslog, postgresql-server, postgresql-contrib

%define src %{_topdir}/..

%description
X-Road security server programs and utilities

%prep
rm -rf proxy
cp -a %{srcdir}/common/proxy .

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
mkdir -p %{buildroot}/etc/xroad/backup.d
mkdir -p %{buildroot}/etc/cron.d

cp -p %{_sourcedir}/proxy/xroad-proxy-setup.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{_sourcedir}/proxy/xroad-initdb.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{_sourcedir}/proxy/xroad-add-admin-user.sh %{buildroot}/usr/share/xroad/bin/
cp -p %{_sourcedir}/proxy/xroad.pam %{buildroot}/etc/pam.d/xroad
cp -p %{_sourcedir}/proxy/xroad-*.service %{buildroot}%{_unitdir}
cp -p %{srcdir}/../../../proxy/build/libs/proxy-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{srcdir}/default-configuration/proxy.ini %{buildroot}/etc/xroad/conf.d
cp -p %{srcdir}/default-configuration/override-rhel-proxy.ini %{buildroot}/etc/xroad/conf.d/
cp -p %{srcdir}/default-configuration/proxy-logback.xml %{buildroot}/etc/xroad/conf.d
cp -p %{srcdir}/default-configuration/rsyslog.d/* %{buildroot}/etc/rsyslog.d/
cp -p %{srcdir}/ubuntu/generic/xroad-proxy.logrotate %{buildroot}/etc/logrotate.d/xroad-proxy
cp -p %{srcdir}/../../../LICENSE.txt %{buildroot}/usr/share/doc/%{name}/LICENSE.txt
cp -p %{srcdir}/../../../securityserver-LICENSE.info %{buildroot}/usr/share/doc/%{name}/securityserver-LICENSE.info
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/%{name}/CHANGELOG.md
cp -p %{srcdir}/common/proxy/etc/xroad/backup.d/??_xroad-proxy %{buildroot}/etc/xroad/backup.d/
cp -p %{_sourcedir}/proxy/xroad-proxy %{buildroot}/etc/cron.d/

ln -s /usr/share/xroad/jlib/proxy-1.0.jar %{buildroot}/usr/share/xroad/jlib/proxy.jar
ln -s /usr/share/xroad/bin/xroad-add-admin-user.sh %{buildroot}/usr/bin/xroad-add-admin-user

%clean
rm -rf %{buildroot}

%files
%defattr(0640,xroad,xroad,0751)
%config /etc/xroad/services/proxy.conf
%config /etc/xroad/conf.d/proxy.ini
%config /etc/xroad/conf.d/override-rhel-proxy.ini
%config /etc/xroad/conf.d/proxy-logback.xml
%config /etc/xroad/jetty/clientproxy.xml
%config /etc/xroad/jetty/serverproxy.xml
%config /etc/xroad/jetty/ocsp-responder.xml
%config /etc/xroad/services/jetty.conf
%config(noreplace) %attr(644,root,root) /etc/pam.d/xroad
%attr(0440,xroad,xroad) %config /etc/xroad/backup.d/??_xroad-proxy

%defattr(-,root,root,-)
%attr(644,root,root) %{_unitdir}/xroad-proxy.service

%config %attr(644,root,root) /etc/logrotate.d/xroad-proxy
%config %attr(644,root,root) /etc/rsyslog.d/40-xroad.conf
%config %attr(644,root,root) /etc/rsyslog.d/90-udp.conf
%config %attr(644,root,root) /etc/sudoers.d/xroad-proxy
%config %attr(644,root,root) /etc/cron.d/xroad-proxy

%attr(550,root,xroad) /usr/share/xroad/bin/xroad-proxy
%attr(540,root,root) /usr/share/xroad/scripts/xroad-proxy-setup.sh
%attr(540,root,root) /usr/share/xroad/scripts/xroad-initdb.sh
%attr(540,root,root) /usr/share/xroad/bin/xroad-add-admin-user.sh
%attr(540,root,root) /usr/share/xroad/scripts/setup_serverconf_db.sh

/usr/bin/xroad-add-admin-user
/usr/share/xroad/db/serverconf-changelog.xml
/usr/share/xroad/db/serverconf-legacy-changelog.xml
/usr/share/xroad/db/serverconf
/usr/share/xroad/db/backup_and_remove_non-member_permissions.sh
/usr/share/xroad/jlib/proxy*.jar
/usr/share/xroad/scripts/backup_db.sh
/usr/share/xroad/scripts/restore_db.sh
/usr/share/xroad/scripts/verify_internal_configuration.sh
/usr/share/xroad/scripts/backup_xroad_proxy_configuration.sh
/usr/share/xroad/scripts/restore_xroad_proxy_configuration.sh
/usr/share/xroad/scripts/autobackup_xroad_proxy_configuration.sh
/usr/share/xroad/scripts/get_security_server_id.sh
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/securityserver-LICENSE.info
%doc /usr/share/doc/%{name}/CHANGELOG.md

%pre
echo DEBUG pre >> /install-xroad.log
date >> /install-xroad.log
if [ $1 -gt 1 ] ; then
    # upgrade
    # remove the previous port forwarding rules (if any)
    if [ -e /etc/sysconfig/xroad-proxy ]; then
        source /etc/sysconfig/xroad-proxy
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

if [ $1 -gt 1 ] ; then
    # upgrade
    # migrate from client-fastest-connecting-ssl-use-uri-cache to client-fastest-connecting-ssl-uri-cache-period
    local_ini=/etc/xroad/conf.d/local.ini
    local_ini_value=$(crudini --get ${local_ini} proxy client-fastest-connecting-ssl-use-uri-cache 2>/dev/null)
    if [[ -n "$local_ini_value" ]];
      then
        echo "client-fastest-connecting-ssl-use-uri-cache present in local.ini, perform migration to client-fastest-connecting-ssl-uri-cache-period"
        if [ "$local_ini_value" = true ] ;
          then
            echo "client-fastest-connecting-ssl-use-uri-cache=true, no action needed, use default value"
          else
            echo "client-fastest-connecting-ssl-use-uri-cache=false, set client-fastest-connecting-ssl-uri-cache-period=0"
            crudini --set ${local_ini} proxy client-fastest-connecting-ssl-uri-cache-period 0
          fi
        crudini --del ${local_ini} proxy client-fastest-connecting-ssl-use-uri-cache
      else
        echo "client-fastest-connecting-ssl-use-uri-cache not present in local.ini, use default value"
      fi
fi

/usr/share/xroad/scripts/xroad-proxy-setup.sh

if [ $1 -gt 1 ]; then
    # upgrade
    if grep -q "^6\.7\." %{_localstatedir}/lib/rpm-state/%{name}/prev-version; then
        # 6.7.x -> 6.8 specific migration
        bash /usr/share/xroad/db/backup_and_remove_non-member_permissions.sh >>/var/log/xroad/proxy-install.log
    fi
    rm -rf %{_localstatedir}/lib/rpm-state/%{name}
fi

#parameters:
#1 file_path
#2 old_section
#3 old_key
#4 new_section
#5 new_key
function migrate_conf_value {
    MIGRATION_VALUE="$(crudini --get "$1" "$2" "$3" 2>/dev/null || true)"
    if [ "${MIGRATION_VALUE}" ];
        then
            crudini --set "$1" "$4" "$5" "${MIGRATION_VALUE}"
            echo Configuration migration: "$2"."$3" "->" "$4"."$5"
            crudini --del "$1" "$2" "$3"
    fi
}

#migrating possible local configuration for modified configuration values (for version 6.17.0)
migrate_conf_value /etc/xroad/conf.d/local.ini proxy ocsp-cache-path signer ocsp-cache-path
migrate_conf_value /etc/xroad/conf.d/local.ini proxy enforce-token-pin-policy signer enforce-token-pin-policy

if [ $1 -eq 1 ] && [ -x %{_bindir}/systemctl ]; then
    # initial installation
    %{_bindir}/systemctl try-restart rsyslog.service
fi

%preun
%systemd_preun xroad-proxy.service
%systemd_preun xroad-confclient.service

%postun
%systemd_postun_with_restart xroad-proxy.service
%systemd_postun_with_restart xroad-confclient.service
%systemd_postun_with_restart rsyslogd.service

%posttrans
# restart (if running) nginx after /etc/xroad/nginx/xroad-proxy.conf has (possibly) been removed, so that port 4000 is freed
echo DEBUG posttrans >> /install-xroad.log
date >> /install-xroad.log
echo nginx status now >> /install-xroad.log
service nginx status >> /install-xroad.log
ps -ef | grep nginx >> /install-xroad.log
find / -name nginx.pid >> /install-xroad.log
echo sleeping 10s and restarting if nginx runs >> /install-xroad.log

sleep 10
service nginx status > /dev/null && service nginx restart
sleep 5
date >> /install-xroad.log
echo nginx status now after posttrans >> /install-xroad.log
service nginx status >> /install-xroad.log

%changelog
