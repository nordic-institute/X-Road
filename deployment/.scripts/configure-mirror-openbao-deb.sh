#!/bin/bash

# Configure OpenBao APT Repository (Container Side)
#
# Configures APT to install OpenBao from a mirrored or official repository.
# Uses mirror-openbao-pkgs-deb (Debian remote repo in Artifactory) when credentials
# are present, falls back to official https://pkgs.openbao.org/deb/ otherwise.
#
# The GPG key is on openbao.org (not pkgs.openbao.org) so it is always fetched
# from the official URL regardless of mirror availability.
#
# Usage:
#   ./configure-mirror-openbao-deb.sh <MIRROR_URL> <MIRROR_USER>
#
# Arguments:
#   MIRROR_URL  - Ubuntu mirror URL (e.g., https://artifactory.example.org/artifactory/mirror-ubuntu)
#                 Used to derive the Artifactory base URL for the OpenBao DEB mirror.
#   MIRROR_USER - Username for authentication
#
# Token is read from /run/secrets/mirror_token (Docker) or XROAD_MIRROR_TOKEN env var (Ansible)

configure_openbao_deb() {
    echo "Configuring OpenBao APT repository..."

    local MIRROR_URL="$1"
    local MIRROR_USER="$2"
    local OPENBAO_KEYRING="/usr/share/keyrings/openbao-keyring.asc"
    local OPENBAO_GPG_URL="https://openbao.org/assets/openbao-gpg-pub-20240618.asc"
    local OPENBAO_DEB_URL
    local MIRROR_TOKEN=""

    # Token from Docker secret or environment variable
    if [ -f /run/secrets/mirror_token ]; then
        MIRROR_TOKEN=$(cat /run/secrets/mirror_token)
    elif [ -n "$XROAD_MIRROR_TOKEN" ]; then
        MIRROR_TOKEN="$XROAD_MIRROR_TOKEN"
    fi

    if [ -n "$MIRROR_URL" ] && [ -n "$MIRROR_USER" ] && [ -n "$MIRROR_TOKEN" ]; then
        # Derive Artifactory base URL from Ubuntu mirror URL by stripping the mirror-ubuntu path segment
        local MIRROR_BASE_URL
        MIRROR_BASE_URL=$(echo "$MIRROR_URL" | sed -E 's|/mirror-ubuntu(-ports|-archive)?(/.*)?$||')
        OPENBAO_DEB_URL="${MIRROR_BASE_URL}/mirror-openbao-pkgs-deb"
        echo "Using mirrored OpenBao DEB repository: $OPENBAO_DEB_URL"
        # Authentication for the mirror host is already configured in /etc/apt/auth.conf.d/mirror.conf
        # by configure-mirror-apt.sh (same Artifactory instance).
    else
        OPENBAO_DEB_URL="https://pkgs.openbao.org/deb"
        echo "Using official OpenBao DEB repository: $OPENBAO_DEB_URL"
    fi

    # GPG key is on openbao.org, not in the packages mirror — always fetch from official URL
    mkdir -p /usr/share/keyrings
    if command -v curl >/dev/null; then
        curl -fsSL -o "$OPENBAO_KEYRING" "$OPENBAO_GPG_URL"
    elif command -v wget >/dev/null; then
        wget -qO "$OPENBAO_KEYRING" "$OPENBAO_GPG_URL"
    else
        echo "ERROR: Neither curl nor wget found. Cannot download OpenBao GPG key."
        return 1
    fi || { echo "ERROR: Failed to download OpenBao GPG key from $OPENBAO_GPG_URL. Cannot configure OpenBao DEB repo."; return 1; }

    echo "deb [signed-by=$OPENBAO_KEYRING] $OPENBAO_DEB_URL/ stable main" \
        > /etc/apt/sources.list.d/openbao.list

    echo "OpenBao APT repository configured."
}

# EXECUTION GUARD
configure_openbao_deb "$1" "$2"
