%include %{_specdir}/common.inc

Name:               xroad-proxy-ui-api
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            X-Road proxy UI REST API
Group:              Applications/Internet
License:            MIT
BuildRequires:      systemd
Requires(post):     systemd
Requires(preun):    systemd
Requires(postun):   systemd
Requires:           iproute, hostname
Requires:           xroad-base = %version-%release, xroad-proxy = %version-%release

%define src %{_topdir}/..

%description
REST API for X-Road proxy UI and management operations

%prep
rm -rf proxy-ui-api
cp -a %{srcdir}/common/proxy-ui-api .

%build

%install
cd proxy-ui-api
cp -a * %{buildroot}

mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}%{_bindir}
mkdir -p %{buildroot}/usr/share/xroad/jlib
mkdir -p %{buildroot}/usr/share/xroad/scripts
mkdir -p %{buildroot}/usr/share/doc/%{name}
mkdir -p %{buildroot}/etc/xroad/conf.d
mkdir -p %{buildroot}/usr/share/doc/xroad/archive-server

cp -p %{_sourcedir}/proxy-ui-api/xroad-proxy-ui-api.service %{buildroot}%{_unitdir}
cp -p %{srcdir}/../../../../src/security-server/admin-service/application/build/libs/proxy-ui-api-1.0.jar %{buildroot}/usr/share/xroad/jlib/
cp -p %{srcdir}/default-configuration/acme.example.yml %{buildroot}/etc/xroad/conf.d
cp -p %{srcdir}/default-configuration/mail.example.yml %{buildroot}/etc/xroad/conf.d
cp -p %{srcdir}/../../../../src/LICENSE.txt %{buildroot}/usr/share/doc/%{name}/LICENSE.txt
cp -p %{srcdir}/../../../../src/3RD-PARTY-NOTICES.txt %{buildroot}/usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
cp -p %{srcdir}/../../../../CHANGELOG.md %{buildroot}/usr/share/doc/%{name}/CHANGELOG.md

ln -s /usr/share/xroad/jlib/proxy-ui-api-1.0.jar %{buildroot}/usr/share/xroad/jlib/proxy-ui-api.jar

cp -p %{srcdir}/../../../../src/security-server/admin-service/message-log-archiver/scripts/archive-http-transporter.sh %{buildroot}/usr/share/xroad/scripts
cp -p %{srcdir}/../../../../src/security-server/admin-service/message-log-archiver/scripts/demo-upload.pl %{buildroot}/usr/share/doc/xroad/archive-server/

%clean
rm -rf %{buildroot}

%files
%defattr(0640,xroad,xroad,0751)
%config /etc/xroad/services/proxy-ui-api.conf
%config /etc/xroad/conf.d/acme.example.yml
%config /etc/xroad/conf.d/mail.example.yml
%attr(644,root,root) %{_unitdir}/xroad-proxy-ui-api.service
%attr(755,root,root) /usr/share/xroad/bin/xroad-proxy-ui-api
%defattr(-,root,root,-)
/usr/share/xroad/jlib/proxy-ui-api*.jar
%doc /usr/share/doc/%{name}/LICENSE.txt
%doc /usr/share/doc/%{name}/3RD-PARTY-NOTICES.txt
%doc /usr/share/doc/%{name}/CHANGELOG.md
/usr/share/xroad/scripts/archive-http-transporter.sh
/usr/share/doc/xroad/archive-server/demo-upload.pl

%pre -p /bin/bash
%upgrade_check

mkdir -p %{_localstatedir}/lib/rpm-state/%{name}
if systemctl is-active %{name} &> /dev/null; then
  touch "%{_localstatedir}/lib/rpm-state/%{name}/active"
fi

if [ "$1" -gt 1 ]; then
  rpm -q %{name} --queryformat="%%{version}" &> "%{_localstatedir}/lib/rpm-state/%{name}/prev-version"
fi

%post
%systemd_post xroad-proxy-ui-api.service

if [ $1 -gt 1 ] ; then
  rm -f "%{_localstatedir}/lib/rpm-state/%{name}/prev-version" >/dev/null 2>&1 || :
fi

# create TLS certificate provisioning properties
CONFIG_FILE="/etc/xroad/conf.d/local-tls.yaml"
HOST=$(hostname -f)
if (( ${#HOST} > 64 )); then
    HOST="$(hostname -s)"
fi
IP_LIST=$(ip addr | grep 'scope global' | awk '{split($2,a,"/"); print a[1]}' | paste -sd "," -)
DNS_LIST="$(hostname -f)$(hostname -s)"
if ! /usr/share/xroad/scripts/yaml_helper.sh exists "$CONFIG_FILE" 'xroad.proxy-ui-api.tls.certificate-provisioning.common-name' &>/dev/null \
   && ! /usr/share/xroad/scripts/yaml_helper.sh exists "$CONFIG_FILE" 'xroad.proxy-ui-api.tls.certificate-provisioning.alt-names' &>/dev/null \
   && ! /usr/share/xroad/scripts/yaml_helper.sh exists "$CONFIG_FILE" 'xroad.proxy-ui-api.tls.certificate-provisioning.ip-subject-alt-names' &>/dev/null; then

    echo "Setting proxy-ui-api TLS certificate provisioning properties in $CONFIG_FILE"
    /usr/share/xroad/scripts/yaml_helper.sh set "$CONFIG_FILE" "xroad.proxy-ui-api.tls.certificate-provisioning.common-name" "$HOST"
    /usr/share/xroad/scripts/yaml_helper.sh set "$CONFIG_FILE" "xroad.proxy-ui-api.tls.certificate-provisioning.alt-names" "$DNS_LIST"
    /usr/share/xroad/scripts/yaml_helper.sh set "$CONFIG_FILE" "xroad.proxy-ui-api.tls.certificate-provisioning.ip-subject-alt-names" "$IP_LIST"
else
  echo "Skipping setting proxy-ui-api TLS certificate provisioning properties in $CONFIG_FILE, already set"
fi

%preun
%systemd_preun xroad-proxy-ui-api.service

%postun
%systemd_postun_with_restart xroad-proxy-ui-api.service

%changelog
