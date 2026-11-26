#!/bin/bash

# Mirror APT Configuration (Container Side)
#
# Configures APT to use a package mirror as a HIGH-PRIORITY OVERLAY.
# Original sources are preserved - mirror is added with priority pinning.
# Handles authentication and dynamic OS detection. TLS verification is disabled
# (GPG signature verification still protects package integrity).
#
# Usage:
#   ./configure-mirror-apt.sh <URL> <USER>
#
# Arguments:
#   URL      - Mirror URL (e.g., https://artifactory.example.org/artifactory/mirror-ubuntu)
#   USER     - Username for authentication
#
# Credentials:
#   Docker build: the mirror_netrc secret, mounted at /run/secrets/mirror_netrc, holds the apt
#     auth line. No credential file is written here; every RUN that runs apt mounts the secret
#     at /etc/apt/auth.conf.d/mirror.conf.
#   Ansible: the XROAD_MIRROR_TOKEN env var; the auth file is written and kept.

NETRC_SECRET=/run/secrets/mirror_netrc

mirror_apt_host() {
    printf '%s\n' "$1" | sed -e 's|^[^/]*//||' -e 's|/.*$||'
}

setup_mirror() {
    echo "Starting Mirror APT Configuration..."

    # 1. Resolve Arguments and Credentials
    # -------------------------------------------------------------------------
    MIRROR_URL="$1"
    MIRROR_USER="$2"

    if [ -s "$NETRC_SECRET" ] || [ -n "$XROAD_MIRROR_TOKEN" ]; then
        HAS_CREDENTIAL=1
    else
        HAS_CREDENTIAL=""
    fi

    if [ -z "$MIRROR_URL" ] || [ -z "$MIRROR_USER" ] || [ -z "$HAS_CREDENTIAL" ]; then
        echo "Mirror credentials (URL, USER, TOKEN) not fully present. Skipping mirror setup."
        echo "Using default public mirrors."
        return 0
    fi

    echo "Configuring package mirror: $MIRROR_URL"
    MIRROR_HOST=$(mirror_apt_host "$MIRROR_URL")

    # 2. Configure Authentication (Machine Login)
    # -------------------------------------------------------------------------
    if [ ! -s "$NETRC_SECRET" ]; then
        mkdir -p /etc/apt/auth.conf.d
        (
            umask 077
            printf 'machine %s login %s password %s\n' "$MIRROR_HOST" "$MIRROR_USER" "$XROAD_MIRROR_TOKEN" \
                > /etc/apt/auth.conf.d/mirror.conf
        )
        chmod 600 /etc/apt/auth.conf.d/mirror.conf
    fi

    # 3. Disable TLS verification for mirror (GPG signature verification still protects packages)
    # -------------------------------------------------------------------------
    echo 'Acquire::https::Verify-Peer "false";' > /etc/apt/apt.conf.d/99mirror-no-verify
    echo 'Acquire::https::Verify-Host "false";' >> /etc/apt/apt.conf.d/99mirror-no-verify

    # 4. Configure APT Sources as OVERLAY (Keep original sources)
    # -------------------------------------------------------------------------
    CODENAME="resolute" # Default
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        if [ -n "$VERSION_CODENAME" ]; then
            CODENAME="$VERSION_CODENAME"
        fi
    fi
    echo "Detected OS Codename: $CODENAME"

    # Architecture-aware URL adjustment
    # Ubuntu has separate repositories for x86_64 (archive.ubuntu.com) and other archs (ports.ubuntu.com)
    # Mirrors:
    #   - mirror-ubuntu-archive (amd64) -> mirrors archive.ubuntu.com
    #   - mirror-ubuntu-ports (arm64)   -> mirrors ports.ubuntu.com
    ARCH=$(dpkg --print-architecture 2>/dev/null || uname -m)
    case "$ARCH" in
        x86_64)  ARCH="amd64" ;;
        aarch64) ARCH="arm64" ;;
    esac

    echo "Detected Architecture: $ARCH"
    if [ "$ARCH" = "amd64" ]; then
        # Use archive mirror for amd64 (mirrors archive.ubuntu.com)
        MIRROR_URL=$(echo "$MIRROR_URL" | sed -E 's/mirror-ubuntu(-ports|-archive)?/mirror-ubuntu-archive/')
    elif [ "$ARCH" = "arm64" ]; then
        # Use ports mirror for arm64 (mirrors ports.ubuntu.com)
        MIRROR_URL=$(echo "$MIRROR_URL" | sed -E 's/mirror-ubuntu(-ports|-archive)?/mirror-ubuntu-ports/')
    fi
    echo "Adjusted Mirror URL: $MIRROR_URL"

    # Create mirror source file as OVERLAY (original sources remain intact)
    # Use "00-" prefix so it sorts first alphabetically
    echo "Adding mirror overlay source: /etc/apt/sources.list.d/00-mirror.sources"
    cat <<EOF > /etc/apt/sources.list.d/00-mirror.sources
Types: deb
URIs: $MIRROR_URL
Suites: $CODENAME $CODENAME-updates $CODENAME-backports $CODENAME-security
Components: main restricted universe multiverse
EOF

    # Set highest priority for mirror (Pin-Priority > 1000 forces installation)
    mkdir -p /etc/apt/preferences.d
    cat <<EOF > /etc/apt/preferences.d/00-mirror
Package: *
Pin: origin ${MIRROR_HOST}
Pin-Priority: 1001
EOF

    echo "Mirror APT configuration complete (overlay mode - original sources preserved)."
}

# EXECUTION GUARD
setup_mirror "$1" "$2"
