%include %{_specdir}/common.inc
# produce .elX dist tag on both centos and redhat
%define dist %(/usr/lib/rpm/redhat/dist.sh)

Name:               xroad-secret-store-local
Version:            %{xroad_version}
# release tag, e.g. 0.201508070816.el7 for snapshots and 1.el7 (for final releases)
Release:            %{rel}%{?snapshot}%{?dist}
Summary:            Meta-package for X-Road remote secret store dependencies
Group:              Applications/Internet
License:            MIT
Requires:           jq, bao >= 2.0.0
Requires:           xroad-base = %version-%release
Conflicts:          xroad-secret-store-local-remote

%description
X-Road OpenBao Auto Unseal Service

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
cp -p %{srcdir}/common/xroad-secret-store-local/usr/share/xroad/scripts/secret-store-unseal.sh %{buildroot}/usr/share/xroad/scripts/
cp -p %{srcdir}/common/xroad-secret-store-local/usr/share/xroad/scripts/secret-store-setup.sh %{buildroot}/usr/share/xroad/scripts/

%files
%defattr(0640,xroad,xroad,0751)
%attr(644,root,root) %{_unitdir}/xroad-secret-store-local.service
%config /etc/xroad/services/secret-store-local.conf
%attr(554,root,xroad)  /usr/share/xroad/scripts/secret-store-unseal.sh
%attr(554,root,xroad) /usr/share/xroad/scripts/secret-store-setup.sh

%pre -p /bin/bash
%upgrade_check

set -e

# Function to handle errors - only clean up on failure
cleanup() {
    if [ $? -ne 0 ]; then
        echo "Installation failed, cleaning up..."
        if [ -d "/opt/openbao/tls" ]; then
            rm -f /opt/openbao/tls/tls.{key,crt} 2>/dev/null || true
        fi
        rm -f /etc/pki/ca-trust/source/anchors/openbao.crt 2>/dev/null || true
    fi
}

trap cleanup EXIT

if [ $1 -eq 1 ] || [ $1 -eq 2 ]; then  # 1 = fresh install, 2 = upgrade
    # Ensure directory exists and has proper permissions
    install -d -m 750 /opt/openbao/tls
    chown openbao:openbao /opt/openbao/tls

    echo "Generating OpenBao TLS certificates..."
    # Generate in temporary location first
    TEMP_DIR=$(mktemp -d)
    cd "$TEMP_DIR" || exit 1

    # Generate certificates with proper permissions
    if ! openssl req \
        -out tls.crt \
        -new \
        -keyout tls.key \
        -newkey rsa:4096 \
        -nodes \
        -sha256 \
        -x509 \
        -subj "/O=OpenBao/CN=OpenBao" \
        -days 7300 \
        -addext "subjectAltName = IP:127.0.0.1" \
        -addext "keyUsage = digitalSignature,keyEncipherment" \
        -addext "extendedKeyUsage = serverAuth"; then
        echo "Failed to generate certificates"
        exit 1
    fi

    # Set proper permissions and ownership
    chmod 640 tls.key tls.crt
    chown openbao:openbao tls.key tls.crt

    # Move files to final location
    mv tls.key tls.crt /opt/openbao/tls/

    # Install certificate to system
    install -m 644 /opt/openbao/tls/tls.crt /etc/pki/ca-trust/source/anchors/openbao.crt
    update-ca-trust

    # Cleanup temp directory
    rm -rf "$TEMP_DIR"
fi

%post
if [ $1 -eq 1 ]; then  # $1 == 1 means fresh install, $1 == 2 means upgrade
    # Enable and start service
    if ! systemctl enable openbao.service; then
        echo "Failed to enable OpenBao service"
        exit 1
    fi

    if ! systemctl start openbao.service; then
        echo "Failed to start OpenBao service"
        exit 1
    fi

    BAO_ADDR='https://127.0.0.1:8200'
    TMP_INIT_FILE="/tmp/bao-init.json"
    UNSEAL_KEYS_FILE="/etc/xroad/secret-store-unseal-keys.json"
    ROOT_TOKEN_FILE="/etc/xroad/secret-store-root-token"

    echo "Waiting for OpenBao to be ready..."
    for i in $(seq 1 30); do
        if curl -sf "${BAO_ADDR}/v1/sys/health" >/dev/null 2>&1; then
            break
        fi
        sleep 1
    done

    echo "Initializing OpenBao.."
    if ! bao operator init -key-shares=3 -key-threshold=2 -format=json >${TMP_INIT_FILE}; then
        echo "Failed to initialize OpenBao"
        exit 1
    fi

    jq -r '.unseal_keys_b64' ${TMP_INIT_FILE} >${UNSEAL_KEYS_FILE}
    jq -r '.root_token' ${TMP_INIT_FILE} >${ROOT_TOKEN_FILE}

    rm -f ${TMP_INIT_FILE}

    echo "Running unseal service.."
    systemctl enable xroad-secret-store-local.service
    systemctl start xroad-secret-store-local.service

    /usr/share/xroad/scripts/secret-store-setup.sh
else
    echo "Upgrade detected, skipping initialization"
fi
