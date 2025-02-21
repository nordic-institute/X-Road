%include %{_specdir}/common.inc
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-secret-store-local
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            Meta-package for local secret store dependencies
Group:              Applications/Internet
License:            MIT
Requires:           jq, bao >= 2.0.0
Requires:           xroad-base = %version-%release
Conflicts:          xroad-secret-store-remote

%description
Installs OpenBao locally and automatically provisions it to align with X-Road

%clean
rm -rf %{buildroot}

%prep

%build

%install
mkdir -p %{buildroot}%{_unitdir}
mkdir -p %{buildroot}/usr/share/xroad/scripts/
mkdir -p %{buildroot}/etc/xroad/services/

cp -p %{_sourcedir}/secret-store-local/xroad-secret-store-local.service %{buildroot}%{_unitdir}
cp -p %{srcdir}/common/xroad-secret-store-local/etc/xroad/services/secret-store-local.conf %{buildroot}/etc/xroad/services/
cp -p %{srcdir}/common/xroad-secret-store-local/usr/share/xroad/scripts/secret-store-generate-tls-certificate.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{srcdir}/common/xroad-secret-store-local/usr/share/xroad/scripts/secret-store-init.sh %{buildroot}/usr/share/xroad/scripts/

%files
%defattr(0640,xroad,xroad,0751)
%attr(644,root,root) %{_unitdir}/xroad-secret-store-local.service
%config /etc/xroad/services/secret-store-local.conf
%attr(554,root,xroad) /usr/share/xroad/scripts/secret-store-generate-tls-certificate.sh
%attr(554,root,xroad) /usr/share/xroad/scripts/secret-store-init.sh

%pre -p /bin/bash
%upgrade_check


%post
if [ $1 -eq 1 ]; then  # $1 == 1 means fresh install, $1 == 2 means upgrade
    /usr/share/xroad/scripts/secret-store-generate-tls-certificate.sh
    # Install generated certificate to system
    install -m 644 /opt/openbao/tls/tls.crt /etc/pki/ca-trust/source/anchors/openbao.crt
    update-ca-trust

    # Enable and start service
    if ! systemctl enable openbao.service; then
        echo "Failed to enable OpenBao service"
        exit 1
    fi

    if ! systemctl restart openbao.service; then
        echo "Failed to restart OpenBao service"
        exit 1
    fi

    echo "Waiting for OpenBao to be ready..."
    for i in $(seq 1 30); do
        if curl -sf "${BAO_ADDR}/v1/sys/health" >/dev/null 2>&1; then
            break
        fi
        sleep 1
    done

    echo "Initializing OpenBao.."
    systemctl enable xroad-secret-store-local.service
    systemctl start xroad-secret-store-local.service
else
    echo "Upgrade detected, skipping initialization"
fi
