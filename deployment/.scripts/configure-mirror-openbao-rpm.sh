#!/bin/bash

# Configure OpenBao YUM/DNF Repository (Container Side)
#
# Configures YUM/DNF to install OpenBao from a mirrored or official repository.
# Uses mirror-openbao-pkgs-rpm (RPM remote repo in Artifactory) when credentials
# are present, falls back to official https://pkgs.openbao.org/rpm/ otherwise.
#
# The GPG key is on openbao.org (not pkgs.openbao.org) so it is always fetched
# from the official URL regardless of mirror availability.
#
# Usage:
#   ./configure-mirror-openbao-rpm.sh <MIRROR_BASE_URL> <MIRROR_USER>
#
# Arguments:
#   MIRROR_BASE_URL - Artifactory base URL (e.g., https://artifactory.example.org/artifactory)
#   MIRROR_USER     - Username for authentication
#
# Token is read from /run/secrets/mirror_token (Docker) or XROAD_MIRROR_TOKEN env var (Ansible)

configure_openbao_rpm() {
    echo "Configuring OpenBao YUM/DNF repository..."

    local MIRROR_BASE_URL="$1"
    local MIRROR_USER="$2"
    local OPENBAO_GPG_URL="https://openbao.org/assets/openbao-gpg-pub-20240618.asc"
    local GPG_KEY_FILE="/etc/pki/rpm-gpg/openbao-gpg-pub-20240618.asc"
    local OPENBAO_RPM_URL
    local REPO_USER=""
    local REPO_PASSWORD=""
    local MIRROR_TOKEN=""

    # Token from Docker secret or environment variable
    if [ -f /run/secrets/mirror_token ]; then
        MIRROR_TOKEN=$(cat /run/secrets/mirror_token)
    elif [ -n "$XROAD_MIRROR_TOKEN" ]; then
        MIRROR_TOKEN="$XROAD_MIRROR_TOKEN"
    fi

    local ARCH
    ARCH=$(uname -m)

    if [ -n "$MIRROR_BASE_URL" ] && [ -n "$MIRROR_USER" ] && [ -n "$MIRROR_TOKEN" ]; then
        OPENBAO_RPM_URL="${MIRROR_BASE_URL}/mirror-openbao-pkgs-rpm/${ARCH}"
        REPO_USER="$MIRROR_USER"
        REPO_PASSWORD="$MIRROR_TOKEN"
        echo "Using mirrored OpenBao RPM repository: $OPENBAO_RPM_URL"
    else
        OPENBAO_RPM_URL="https://pkgs.openbao.org/rpm/\$basearch"
        echo "Using official OpenBao RPM repository: $OPENBAO_RPM_URL"
    fi

    # GPG key is on openbao.org, not in the packages mirror — always fetch from official URL.
    # Skip repo setup with a warning if unreachable.
    mkdir -p /etc/pki/rpm-gpg
    if ! curl -fsSL -o "$GPG_KEY_FILE" "$OPENBAO_GPG_URL"; then
        echo "Warning: Failed to fetch OpenBao GPG key from $OPENBAO_GPG_URL. Skipping OpenBao RPM repo setup."
        return 0
    fi
    rpm --import "$GPG_KEY_FILE"
    echo "OpenBao GPG key imported."

    cat > /etc/yum.repos.d/openbao.repo <<EOF
[openbao]
name=openbao
baseurl=${OPENBAO_RPM_URL}
repo_gpgcheck=0
gpgcheck=1
enabled=1
gpgkey=file://${GPG_KEY_FILE}
sslverify=1
sslcacert=/etc/pki/tls/certs/ca-bundle.crt
metadata_expire=300
${REPO_USER:+username=${REPO_USER}}
${REPO_PASSWORD:+password=${REPO_PASSWORD}}
EOF

    echo "OpenBao YUM/DNF repository configured."
}

# EXECUTION GUARD
configure_openbao_rpm "$1" "$2"
