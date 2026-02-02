#!/bin/bash

# Artifactory APT Configuration (Container Side)
#
# Configures APT to use Artifactory mirror as a HIGH-PRIORITY OVERLAY.
# Original sources are preserved - Artifactory is added with priority pinning.
# Handles Authentication, SSL/CA certificates, and dynamic OS detection.
#
# Usage:
#   ./configure-artifactory-apt.sh <URL> <USER> [CA_CERT]
#
# Arguments:
#   URL      - Artifactory mirror URL (e.g., https://artifactory.example.org/artifactory/mirror-ubuntu)
#   USER     - Username for authentication
#   CA_CERT  - Optional CA certificate content
#
# Token is read from /run/secrets/artifactory_token (Docker) or ARTIFACTORY_TOKEN env var (Ansible)

setup_artifactory_mirror() {
    echo "Starting Artifactory APT Configuration..."

    # 1. Resolve Arguments and Credentials
    # -------------------------------------------------------------------------
    ARTIFACTORY_URL="$1"
    ARTIFACTORY_USER="$2"
    ARTIFACTORY_CA_CERT="$3"

    # Token from Docker secret or environment variable
    if [ -f /run/secrets/artifactory_token ]; then
        ARTIFACTORY_TOKEN=$(cat /run/secrets/artifactory_token)
    fi

    if [ -z "$ARTIFACTORY_URL" ] || [ -z "$ARTIFACTORY_USER" ] || [ -z "$ARTIFACTORY_TOKEN" ]; then
        echo "Artifactory credentials (URL, USER, TOKEN) not fully present. Skipping Artifactory setup."
        echo "Using default public mirrors."
        return 0
    fi

    echo "Configuring Artifactory mirror: $ARTIFACTORY_URL"
    ARTIFACTORY_HOST=$(echo "$ARTIFACTORY_URL" | sed -e 's|^[^/]*//||' -e 's|/.*$||')

    # 2. Configure Authentication (Machine Login)
    # -------------------------------------------------------------------------
    mkdir -p /etc/apt/auth.conf.d
    echo "machine $ARTIFACTORY_HOST login $ARTIFACTORY_USER password $ARTIFACTORY_TOKEN" > /etc/apt/auth.conf.d/artifactory.conf
    chmod 600 /etc/apt/auth.conf.d/artifactory.conf

    # 3. Configure SSL / Trusted CA
    # -------------------------------------------------------------------------
    if [ -n "$ARTIFACTORY_CA_CERT" ]; then
        mkdir -p /usr/local/share/ca-certificates
        echo "$ARTIFACTORY_CA_CERT" > /usr/local/share/ca-certificates/artifactory.crt
        chmod 644 /usr/local/share/ca-certificates/artifactory.crt

        # Try to update system CA store (standard Ubuntu/Debian way)
        if command -v update-ca-certificates >/dev/null 2>&1; then
            update-ca-certificates --fresh
            echo "Added Artifactory CA certificate to system store."
        else
            # Fallback to explicit CaInfo if update-ca-certificates is not found
            echo 'Acquire::https::CaInfo "/usr/local/share/ca-certificates/artifactory.crt";' > /etc/apt/apt.conf.d/99artifactory-cert
            echo "Added Artifactory CA certificate via APT CaInfo fallback."
        fi
    fi

    # 4. Configure APT Sources as OVERLAY (Keep original sources)
    # -------------------------------------------------------------------------
    CODENAME="noble" # Default
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        if [ -n "$VERSION_CODENAME" ]; then
            CODENAME="$VERSION_CODENAME"
        fi
    fi
    echo "Detected OS Codename: $CODENAME"

    # Architecture-aware URL adjustment
    # Ubuntu has separate repositories for x86_64 (archive.ubuntu.com) and other archs (ports.ubuntu.com)
    # Artifactory mirrors:
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
        ARTIFACTORY_URL=$(echo "$ARTIFACTORY_URL" | sed -E 's/mirror-ubuntu(-ports|-archive)?/mirror-ubuntu-archive/')
    elif [ "$ARCH" = "arm64" ]; then
        # Use ports mirror for arm64 (mirrors ports.ubuntu.com)
        ARTIFACTORY_URL=$(echo "$ARTIFACTORY_URL" | sed -E 's/mirror-ubuntu(-ports|-archive)?/mirror-ubuntu-ports/')
    fi
    echo "Adjusted Artifactory URL: $ARTIFACTORY_URL"

    # Create Artifactory source file as OVERLAY (original sources remain intact)
    # Use "00-" prefix so it sorts first alphabetically
    echo "Adding Artifactory overlay source: /etc/apt/sources.list.d/00-artifactory.sources"
    cat <<EOF > /etc/apt/sources.list.d/00-artifactory.sources
Types: deb
URIs: $ARTIFACTORY_URL
Suites: $CODENAME $CODENAME-updates $CODENAME-backports $CODENAME-security
Components: main restricted universe multiverse
EOF

    # Set highest priority for Artifactory mirror (Pin-Priority > 1000 forces installation)
    mkdir -p /etc/apt/preferences.d
    cat <<EOF > /etc/apt/preferences.d/00-artifactory
Package: *
Pin: origin ${ARTIFACTORY_HOST}
Pin-Priority: 1001
EOF

    echo "Artifactory APT configuration complete (overlay mode - original sources preserved)."
}

# EXECUTION GUARD
setup_artifactory_mirror "$1" "$2" "$3"
