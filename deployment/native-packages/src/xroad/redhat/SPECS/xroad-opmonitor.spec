%include %{_specdir}/common.inc
# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)
# Ignore python bytecompile errors due to mismatching python versions.
# This is to be able to provide sample scripts with the package with no hassle.
%global _python_bytecompile_errors_terminate_build 0

Name:               xroad-opmonitor
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road operations monitoring daemon
Group:              Applications/Internet
License:            MIT
BuildRequires:      systemd
Requires(post):     systemd
Requires(preun):    systemd
Requires(postun):   systemd
Requires:           xroad-base = %version-%release, xroad-confclient = %version-%release
Requires:           xroad-database >= %version-%release, xroad-database <= %version-%{release}.1

%define src %{_topdir}/..

%description
X-Road operations monitoring daemon

%prep

%build

%install
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}/usr/share/xroad/db/op-monitor/
mkdir -p %{buildroot}/usr/share/xroad/scripts/
mkdir -p %{buildroot}/usr/share/xroad/jlib/
mkdir -p %{buildroot}/usr/share/xroad/jlib/op-monitor
mkdir -p %{buildroot}/usr/share/doc/%{name}
mkdir -p %{buildroot}/usr/bin/
mkdir -p %{buildroot}/etc/xroad/services/
mkdir -p %{buildroot}/usr/share/xroad/bin/
mkdir -p %{buildroot}/etc/xroad/conf.d/
mkdir -p %{buildroot}/etc/xroad/backup.d/

cp -p %{_sourcedir}/opmonitor/xroad-opmonitor.service %{buildroot}%{_unitdir}
cp -p %{_sourcedir}/opmonitor/xroad-opmonitor-initdb.sh %{buildroot}/usr/share/xroad/scripts/
cp -p -r %{srcdir}/../../../../src/service/op-monitor/op-monitor-application/build/quarkus-app/* %{buildroot}/usr/share/xroad/jlib/op-monitor/
cp -p %{srcdir}/common/op-monitor/etc/xroad/services/opmonitor.conf %{buildroot}/etc/xroad/services/
cp -p %{srcdir}/../../../../src/service/op-monitor/op-monitor-db/src/main/resources/liquibase/op-monitor/*.xml %{buildroot}/usr/share/xroad/db/op-monitor/
cp -p %{srcdir}/../../../../src/service/op-monitor/op-monitor-db/src/main/resources/liquibase/op-monitor-changelog.xml %{buildroot}/usr/share/xroad/db/
cp -p %{srcdir}/common/op-monitor/usr/share/xroad/bin/xroad-opmonitor %{buildroot}/usr/share/xroad/bin/
cp -p %{srcdir}/common/op-monitor/usr/share/xroad/scripts/setup_opmonitor_db.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{srcdir}/../../../../src/LICENSE.txt %{buildroot}/usr/share/doc/xroad-opmonitor/
cp -p %{srcdir}/../../../../src/3RD-PARTY-NOTICES.txt %{buildroot}/usr/share/doc/xroad-opmonitor/
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/xroad-opmonitor/
cp -p %{srcdir}/common/op-monitor/etc/xroad/backup.d/??_xroad-opmonitor %{buildroot}/etc/xroad/backup.d/

ln -s /usr/share/xroad/jlib/op-monitor/quarkus-run.jar %{buildroot}/usr/share/xroad/jlib/op-monitor-daemon.jar

%clean
rm -rf %{buildroot}

%files
%defattr(-,xroad,xroad,-)
%config /etc/xroad/services/opmonitor.conf
%attr(0440,xroad,xroad) %config /etc/xroad/backup.d/??_xroad-opmonitor

/usr/share/xroad/db/op-monitor/
/usr/share/xroad/db/op-monitor-changelog.xml

%defattr(-,root,root,-)
%attr(540,root,xroad) /usr/share/xroad/scripts/xroad-opmonitor-initdb.sh
%attr(540,root,xroad) /usr/share/xroad/scripts/setup_opmonitor_db.sh
%attr(554,root,xroad) /usr/share/xroad/bin/xroad-opmonitor
%attr(644,root,root) %{_unitdir}/xroad-opmonitor.service

/usr/share/xroad/jlib/op-monitor/
/usr/share/xroad/jlib/op-monitor-daemon.jar

%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
%doc /usr/share/doc/%{name}/CHANGELOG.md

%pre -p /bin/bash
%upgrade_check

mkdir -p %{_localstatedir}/lib/rpm-state/%{name}
if systemctl is-active %{name} &> /dev/null; then
  touch "%{_localstatedir}/lib/rpm-state/%{name}/active"
fi

%define init_xroad_opmonitor_db()                       \
    /usr/share/xroad/scripts/xroad-opmonitor-initdb.sh

%post

# create TLS certificate provisioning properties
CONFIG_FILE="/etc/xroad/conf.d/local.yaml"
mkdir -p "$(dirname "$CONFIG_FILE")"
[ ! -f "$CONFIG_FILE" ] && touch "$CONFIG_FILE"
HOST=$(hostname -f)
if (( ${#HOST} > 64 )); then
    HOST="$(hostname -s)"
fi
IP_LIST=$(ip addr | grep 'scope global' | awk '{split($2,a,"/"); print a[1]}' | paste -sd "," -)
DNS_LIST="$(hostname -f),$(hostname -s)"
if ! /usr/share/xroad/scripts/yaml_helper.sh exists "$CONFIG_FILE" 'xroad.op-monitor.tls.certificate-provisioning.common-name' &>/dev/null \
   && ! /usr/share/xroad/scripts/yaml_helper.sh exists "$CONFIG_FILE" 'xroad.op-monitor.tls.certificate-provisioning.alt-names' &>/dev/null \
   && ! /usr/share/xroad/scripts/yaml_helper.sh exists "$CONFIG_FILE" 'xroad.op-monitor.tls.certificate-provisioning.ip-subject-alt-names' &>/dev/null; then

    echo "Setting op-monitor TLS certificate provisioning properties in $CONFIG_FILE"
    /usr/share/xroad/scripts/yaml_helper.sh set "$CONFIG_FILE" "xroad.op-monitor.tls.certificate-provisioning.common-name" "$HOST"
    /usr/share/xroad/scripts/yaml_helper.sh set "$CONFIG_FILE" "xroad.op-monitor.tls.certificate-provisioning.alt-names" "$DNS_LIST"
    /usr/share/xroad/scripts/yaml_helper.sh set "$CONFIG_FILE" "xroad.op-monitor.tls.certificate-provisioning.ip-subject-alt-names" "$IP_LIST"
else
  echo "Skipping setting op-monitor TLS certificate provisioning properties in $CONFIG_FILE, already set"
fi

%systemd_post xroad-opmonitor.service

# RHEL7 java-21-* package makes java binaries available since %post scriptlet
%if 0%{?el7}
%init_xroad_opmonitor_db
%endif

%preun
%systemd_preun xroad-opmonitor.service

%postun
%systemd_postun_with_restart xroad-opmonitor.service

%posttrans
# RHEL8/9 java-21-* package makes java binaries available since %posttrans scriptlet
%if 0%{?el8} || 0%{?el9}
%init_xroad_opmonitor_db
%endif

%changelog

