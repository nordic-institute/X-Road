%include %{_specdir}/common.inc
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
Requires:           xroad-base = %version-%release, xroad-confclient = %version-%release, xroad-signer = %version-%release, rsyslog
Requires:           xroad-database >= %version-%release, xroad-database <= %version-%{release}.1

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
mkdir -p %{buildroot}/usr/share/xroad/jlib/proxy
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

cp -p %{_sourcedir}/proxy/xroad-initdb.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{_sourcedir}/proxy/xroad-add-admin-user.sh %{buildroot}/usr/share/xroad/bin/
cp -p %{_sourcedir}/proxy/xroad.pam %{buildroot}/etc/pam.d/xroad
cp -p %{_sourcedir}/proxy/xroad-*.service %{buildroot}%{_unitdir}
cp -a %{srcdir}/../../../../src/security-server/admin-service/infra-jpa/build/resources/main/liquibase/* %{buildroot}/usr/share/xroad/db/
cp -a %{srcdir}/../../../../src/lib/messagelog-core/src/main/resources/liquibase/* %{buildroot}/usr/share/xroad/db/
cp -p -r %{srcdir}/../../../../src/service/proxy/proxy-application/build/quarkus-app/* %{buildroot}/usr/share/xroad/jlib/proxy
cp -p %{srcdir}/default-configuration/rsyslog.d/* %{buildroot}/etc/rsyslog.d/
cp -p %{srcdir}/ubuntu/generic/xroad-proxy.logrotate %{buildroot}/etc/logrotate.d/xroad-proxy
cp -p %{srcdir}/../../../../src/LICENSE.txt %{buildroot}/usr/share/doc/%{name}/LICENSE.txt
cp -p %{srcdir}/../../../../src/3RD-PARTY-NOTICES.txt %{buildroot}/usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/%{name}/CHANGELOG.md
cp -p %{srcdir}/common/proxy/etc/xroad/backup.d/??_xroad-proxy %{buildroot}/etc/xroad/backup.d/

ln -s /usr/share/xroad/jlib/proxy/quarkus-run.jar %{buildroot}/usr/share/xroad/jlib/proxy.jar
ln -s /usr/share/xroad/bin/xroad-add-admin-user.sh %{buildroot}/usr/bin/xroad-add-admin-user

%clean
rm -rf %{buildroot}

%files
%defattr(0640,xroad,xroad,0751)
%config /etc/xroad/services/proxy.conf

%config(noreplace) %attr(644,root,root) /etc/pam.d/xroad
%attr(0440,xroad,xroad) %config /etc/xroad/backup.d/??_xroad-proxy

%defattr(-,root,root,-)
%attr(644,root,root) %{_unitdir}/xroad-proxy.service

%config %attr(644,root,root) /etc/logrotate.d/xroad-proxy
%config %attr(644,root,root) /etc/rsyslog.d/40-xroad.conf
%config %attr(644,root,root) /etc/rsyslog.d/90-udp.conf
%config %attr(644,root,root) /etc/sudoers.d/xroad-proxy

%attr(550,root,xroad) /usr/share/xroad/bin/xroad-proxy
%attr(540,root,root) /usr/share/xroad/scripts/xroad-initdb.sh
%attr(540,root,root) /usr/share/xroad/bin/xroad-add-admin-user.sh
%attr(540,root,root) /usr/share/xroad/scripts/setup_serverconf_db.sh
%attr(540,root,root) /usr/share/xroad/scripts/setup_messagelog_db.sh

/usr/bin/xroad-add-admin-user
/usr/share/xroad/db/serverconf-changelog.xml
/usr/share/xroad/db/serverconf
/usr/share/xroad/db/messagelog-changelog.xml
/usr/share/xroad/db/messagelog
/usr/share/xroad/db/signer
/usr/share/xroad/db/backup_and_remove_non-member_permissions.sh
/usr/share/xroad/jlib/proxy.jar
/usr/share/xroad/jlib/proxy/
/usr/share/xroad/scripts/proxy_memory_helper.sh
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
%doc /usr/share/doc/%{name}/CHANGELOG.md

%pre -p /bin/bash
%upgrade_check

mkdir -p %{_localstatedir}/lib/rpm-state/%{name}
if systemctl is-active %{name} &> /dev/null; then
  touch "%{_localstatedir}/lib/rpm-state/%{name}/active"
fi

if [ $1 -gt 1 ] ; then
    # upgrade
    # remove the previous port forwarding rules (if any)
    if [ -e /etc/sysconfig/xroad-proxy ]; then
        source /etc/sysconfig/xroad-proxy
    fi

    rpm -q xroad-proxy --queryformat="%%{version}" &> %{_localstatedir}/lib/rpm-state/%{name}/prev-version

fi

%define execute_init_or_update_resources()                                            \
    echo "Update resources: DB";                                                      \
    /usr/share/xroad/scripts/setup_serverconf_db.sh;                                  \
    /usr/share/xroad/scripts/setup_messagelog_db.sh;                                  \
                                                                                      \
    if [ $1 -eq 1 ] && [ -x %{_bindir}/systemctl ]; then                              \
        `# initial installation`;                                                     \
        %{_bindir}/systemctl try-restart rsyslog.service                              \
    fi

%post -p /bin/bash
%systemd_post xroad-proxy.service

if [ $1 -eq 1 ] ; then
    # Initial installation
    if ! /usr/share/xroad/scripts/xroad-initdb.sh; then
      echo "Error: Failed to initialize DB."
      exit 1
    fi
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

# create TLS certificate provisioning properties
CONFIG_FILE="/etc/xroad/conf.d/local.yaml"
mkdir -p "$(dirname "$CONFIG_FILE")"
[ ! -f "$CONFIG_FILE" ] && touch "$CONFIG_FILE"
HOST=$(hostname -f)
if (( ${#HOST} > 64 )); then
    HOST="$(hostname -s)"
fi
IP_LIST=$(ip addr | grep 'scope global' | awk '{split($2,a,"/"); print a[1]}' | paste -sd "," -)
DNS_LIST="$(hostname -f)$(hostname -s)"
if ! /usr/share/xroad/scripts/yaml_helper.sh exists "$CONFIG_FILE" 'xroad.proxy.tls.certificate-provisioning.common-name' &>/dev/null \
   && ! /usr/share/xroad/scripts/yaml_helper.sh exists "$CONFIG_FILE" 'xroad.proxy.tls.certificate-provisioning.alt-names' &>/dev/null \
   && ! /usr/share/xroad/scripts/yaml_helper.sh exists "$CONFIG_FILE" 'xroad.proxy.tls.certificate-provisioning.ip-subject-alt-names' &>/dev/null; then

    echo "Setting proxy internal TLS certificate provisioning properties in $CONFIG_FILE"
    /usr/share/xroad/scripts/yaml_helper.sh set "$CONFIG_FILE" "xroad.proxy.tls.certificate-provisioning.common-name" "$HOST"
    /usr/share/xroad/scripts/yaml_helper.sh set "$CONFIG_FILE" "xroad.proxy.tls.certificate-provisioning.alt-names" "$DNS_LIST"
    /usr/share/xroad/scripts/yaml_helper.sh set "$CONFIG_FILE" "xroad.proxy.tls.certificate-provisioning.ip-subject-alt-names" "$IP_LIST"
else
  echo "Skipping setting proxy internal TLS certificate provisioning properties in $CONFIG_FILE, already set"
fi


mkdir -p /var/spool/xroad; chown xroad:xroad /var/spool/xroad
mkdir -p /var/cache/xroad; chown xroad:xroad /var/cache/xroad
mkdir -p /etc/xroad/globalconf; chown xroad:xroad /etc/xroad/globalconf

# RHEL7 java-21-* package makes java binaries available since %post scriptlet
%if 0%{?el7}
%execute_init_or_update_resources
%endif

%preun
%systemd_preun xroad-proxy.service

%postun
%systemd_postun_with_restart xroad-proxy.service xroad-confclient.service rsyslog.service

%posttrans -p /bin/bash
# restart (if running) nginx after /etc/xroad/nginx/xroad-proxy.conf has (possibly) been removed, so that port 4000 is freed
%systemd_try_restart nginx.service

# RHEL8/9 java-21-* package makes java binaries available since %posttrans scriptlet
%if 0%{?el8} || 0%{?el9}
%execute_init_or_update_resources
%endif

%changelog
