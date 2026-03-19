#!/bin/bash

# Configure OpenBao YUM/DNF Repository (Container Side)
#
# Configures YUM/DNF to install OpenBao from a mirrored or official repository.
# Uses the provided mirror URL when credentials are present, falls back to
# the official https://pkgs.openbao.org/rpm/ otherwise.
#
# The GPG key is on openbao.org (not pkgs.openbao.org) so it is always fetched
# from the official URL regardless of mirror availability.
#
# Usage:
#   ./configure-mirror-openbao-rpm.sh <OPENBAO_MIRROR_URL> <MIRROR_USER>
#
# Arguments:
#   OPENBAO_MIRROR_URL - OpenBao RPM mirror URL (e.g., https://artifactory.example.org/artifactory/mirror-openbao-pkgs-rpm/x86_64)
#   MIRROR_USER        - Username for authentication
#
# Token is read from /run/secrets/mirror_token (Docker) or OPENBAO_MIRROR_TOKEN env var

configure_openbao_rpm() {
    echo "Configuring OpenBao YUM/DNF repository..."

    local OPENBAO_MIRROR_URL="$1"
    local MIRROR_USER="$2"
    local OPENBAO_GPG_URL="https://openbao.org/assets/openbao-gpg-pub-20240618.asc"
    local GPG_KEY_FILE="/etc/pki/rpm-gpg/openbao-gpg-pub-20240618.asc"
    local MIRROR_TOKEN=""

    # Token from Docker secret or environment variable
    if [ -f /run/secrets/mirror_token ]; then
        MIRROR_TOKEN=$(cat /run/secrets/mirror_token)
    elif [ -n "$OPENBAO_MIRROR_TOKEN" ]; then
        MIRROR_TOKEN="$OPENBAO_MIRROR_TOKEN"
    fi

    if [ -n "$OPENBAO_MIRROR_URL" ] && [ -n "$MIRROR_USER" ] && [ -n "$MIRROR_TOKEN" ]; then
        echo "Using mirrored OpenBao RPM repository: $OPENBAO_MIRROR_URL"

        MIRROR_HOST=$(echo "$MIRROR_BASE_URL" | sed -e 's|^[^/]*//||' -e 's|/.*$||')
        cat > /root/.netrc <<EOF
machine $MIRROR_HOST
login $MIRROR_USER
password $MIRROR_TOKEN
EOF
        chmod 600 /root/.netrc
    else
        OPENBAO_MIRROR_URL="https://pkgs.openbao.org/rpm/\$basearch"
        echo "Using official OpenBao RPM repository: $OPENBAO_MIRROR_URL"
    fi

    # GPG key is on openbao.org, not in the packages mirror — always fetch from official URL.
    mkdir -p /etc/pki/rpm-gpg
    if command -v curl >/dev/null; then
        curl -fsSL -o "$GPG_KEY_FILE" "$OPENBAO_GPG_URL"
    elif command -v wget >/dev/null; then
        wget -qO "$GPG_KEY_FILE" "$OPENBAO_GPG_URL"
    else
        echo "ERROR: Neither curl nor wget found. Cannot download OpenBao GPG key."
        return 1
    fi || { echo "ERROR: Failed to fetch OpenBao GPG key from $OPENBAO_GPG_URL. Cannot configure OpenBao RPM repo."; return 1; }
    rpm --import "$GPG_KEY_FILE"
    echo "OpenBao GPG key imported."

    cat > /etc/yum.repos.d/openbao.repo <<EOF
[openbao]
name=openbao
baseurl=${OPENBAO_MIRROR_URL}
repo_gpgcheck=0
gpgcheck=1
enabled=1
gpgkey=file://${GPG_KEY_FILE}
sslverify=1
sslcacert=/etc/pki/tls/certs/ca-bundle.crt
metadata_expire=300
${MIRROR_USER:+username=${MIRROR_USER}}
${MIRROR_TOKEN:+password=${MIRROR_TOKEN}}
EOF

    echo "OpenBao YUM/DNF repository configured."
}

# EXECUTION GUARD
configure_openbao_rpm "$1" "$2"