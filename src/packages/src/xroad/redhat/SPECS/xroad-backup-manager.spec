%include %{_specdir}/common.inc
# do not repack jars
%define __jar_repack %{nil}
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-backup-manager
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road backup manager
Group:              Applications/Internet
License:            MIT
Requires(post):     systemd
Requires(preun):    systemd
Requires(postun):   systemd
BuildRequires:      systemd
Requires:           systemd
Requires:           net-tools, tar
Requires:           xroad-base = %version-%release
Requires:           (xroad-secret-store-local = %version-%release or xroad-secret-store-remote = %version-%release)

%define src %{_topdir}/..

%description
X-Road backup manager

%prep
rm -rf backup-manager
cp -a %{srcdir}/common/backup-manager .

%build

%install
cd backup-manager
cp -a * %{buildroot}

mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}/usr/share/xroad/jlib
mkdir -p %{buildroot}/usr/share/xroad/jlib/backup-manager
mkdir -p %{buildroot}/usr/share/xroad/scripts
mkdir -p %{buildroot}/etc/xroad
mkdir -p %{buildroot}/usr/share/xroad/bin
mkdir -p %{buildroot}/usr/share/doc/%{name}

cp -p %{_sourcedir}/backup-manager/xroad-*.service %{buildroot}%{_unitdir}
cp -p -r %{srcdir}/../../../service/backup-manager/backup-manager-application/build/quarkus-app/* %{buildroot}/usr/share/xroad/jlib/backup-manager
cp -p %{srcdir}/../../../LICENSE.txt %{buildroot}/usr/share/doc/%{name}/LICENSE.txt
cp -p %{srcdir}/../../../3RD-PARTY-NOTICES.txt %{buildroot}/usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/%{name}/CHANGELOG.md

ln -s /usr/share/xroad/jlib/backup-manager/quarkus-run.jar %{buildroot}/usr/share/xroad/jlib/backup-manager.jar

%clean
rm -rf %{buildroot}

%files
%defattr(0640,xroad,xroad,0751)
%config /etc/xroad/services/backup-manager.conf

%defattr(-,root,root,-)
%attr(644,root,root) %{_unitdir}/xroad-backup-manager.service

%attr(550,root,xroad) /usr/share/xroad/bin/xroad-backup-manager

/usr/share/xroad/jlib/backup-manager.jar
/usr/share/xroad/jlib/backup-manager/
/usr/share/xroad/scripts/get_security_server_id.sh
/usr/share/xroad/scripts/read_db_properties.sh
/usr/share/xroad/scripts/backup_db.sh
/usr/share/xroad/scripts/restore_db.sh
/usr/share/xroad/scripts/backup_xroad_proxy_configuration.sh
/usr/share/xroad/scripts/restore_xroad_proxy_configuration.sh
/usr/share/xroad/scripts/autobackup_xroad_proxy_configuration.sh
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
%doc /usr/share/doc/%{name}/CHANGELOG.md

%pre -p /bin/bash
%upgrade_check

mkdir -p %{_localstatedir}/lib/rpm-state/%{name}
if systemctl is-active %{name} &> /dev/null; then
  touch "%{_localstatedir}/lib/rpm-state/%{name}/active"
fi

%define execute_init_or_update_resources()                                            \
    echo "Update resources: GPG";                                                     \
                                                                                      \
    if [ $1 -gt 1 ]; then                                                             \
      `# upgrade, generate gpg keypair when needed`                                   \
      if [ ! -d /etc/xroad/gpghome ] ; then                                           \
        ID=$(/usr/share/xroad/scripts/get_security_server_id.sh)                      \
        if [[ -n "${ID}" ]] ; then                                                    \
          /usr/share/xroad/scripts/generate_gpg_keypair.sh /etc/xroad/gpghome "${ID}" \
        fi                                                                            \
      fi                                                                              \
      `# always fix gpghome ownership`;                                               \
      [ -d /etc/xroad/gpghome ] && chown -R xroad:xroad /etc/xroad/gpghome            \
    fi

%post -p /bin/bash
%systemd_post xroad-backup-manager.service

%execute_init_or_update_resources

%preun
%systemd_preun xroad-backup-manager.service

%postun
%systemd_postun_with_restart xroad-backup-manager.service

%changelog
