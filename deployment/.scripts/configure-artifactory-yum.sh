#!/bin/bash

# Artifactory YUM/DNF Configuration (Container Side)
#
# Configures YUM/DNF to use Artifactory mirror as a HIGH-PRIORITY OVERLAY.
# Original repos are preserved - Artifactory is added with priority=1.
# Handles Authentication, SSL/CA certificates, and dynamic version detection.
#
# Usage:
#   ./configure-artifactory-yum.sh <BASE_URL> <USER> [CA_CERT]
#
# Arguments:
#   BASE_URL - Artifactory base URL (e.g., https://artifactory.niis.org/artifactory)
#   USER     - Username for authentication
#   CA_CERT  - Optional CA certificate content
#
# Token is read from /run/secrets/artifactory_token (Docker) or ARTIFACTORY_TOKEN env var (Ansible)

setup_artifactory_mirror() {
    echo "Starting Artifactory YUM/DNF Configuration..."

    # 1. Resolve Arguments and Credentials
    # -------------------------------------------------------------------------
    ARTIFACTORY_BASE_URL="$1"
    ARTIFACTORY_USER="$2"
    ARTIFACTORY_CA_CERT="$3"

    # Token from Docker secret or environment variable
    if [ -f /run/secrets/artifactory_token ]; then
        ARTIFACTORY_TOKEN=$(cat /run/secrets/artifactory_token)
    fi

    if [ -z "$ARTIFACTORY_BASE_URL" ] || [ -z "$ARTIFACTORY_USER" ] || [ -z "$ARTIFACTORY_TOKEN" ]; then
        echo "Artifactory credentials (BASE_URL, USER, TOKEN) not fully present. Skipping Artifactory setup."
        echo "Using default public mirrors."
        return 0
    fi

    echo "Configuring Artifactory mirror: $ARTIFACTORY_BASE_URL"

    # 2. Detect Rocky Linux Version and Architecture
    # -------------------------------------------------------------------------
    VERSION=""
    ARCH=$(uname -m)

    if [ -f /etc/os-release ]; then
        . /etc/os-release
        if [ "$ID" != "rocky" ]; then
            echo "Unsupported distribution: $ID. Only Rocky Linux is supported. Skipping Artifactory setup."
            return 0
        fi
        VERSION="${VERSION_ID%%.*}"  # Major version only (8 or 9)
    else
        echo "/etc/os-release not found. Cannot detect distribution."
        return 1
    fi

    echo "Detected: Rocky Linux $VERSION ($ARCH)"

    # 3. Configure SSL / Trusted CA
    # -------------------------------------------------------------------------
    if [ -n "$ARTIFACTORY_CA_CERT" ]; then
        mkdir -p /etc/pki/ca-trust/source/anchors
        echo "$ARTIFACTORY_CA_CERT" > /etc/pki/ca-trust/source/anchors/artifactory.crt
        chmod 644 /etc/pki/ca-trust/source/anchors/artifactory.crt

        if command -v update-ca-trust >/dev/null 2>&1; then
            update-ca-trust extract
            echo "Added Artifactory CA certificate to system store."
        fi
    fi

    # 4. Build Repository URLs
    # -------------------------------------------------------------------------
    # Artifactory mirrors the full upstream path structure:
    #   mirror-rocky -> https://dl.rockylinux.org/pub/rocky/
    #   mirror-epel  -> https://dl.fedoraproject.org/pub/epel/
    BASEOS_URL="${ARTIFACTORY_BASE_URL}/mirror-rocky/${VERSION}/BaseOS/${ARCH}/os/"
    APPSTREAM_URL="${ARTIFACTORY_BASE_URL}/mirror-rocky/${VERSION}/AppStream/${ARCH}/os/"
    EXTRAS_URL="${ARTIFACTORY_BASE_URL}/mirror-rocky/${VERSION}/extras/${ARCH}/os/"
    EPEL_URL="${ARTIFACTORY_BASE_URL}/mirror-epel/${VERSION}/Everything/${ARCH}/"

    # 5. Configure Authentication
    # -------------------------------------------------------------------------
    ARTIFACTORY_HOST=$(echo "$ARTIFACTORY_BASE_URL" | sed -e 's|^[^/]*//||' -e 's|/.*$||')
    cat > /root/.netrc <<EOF
machine $ARTIFACTORY_HOST
login $ARTIFACTORY_USER
password $ARTIFACTORY_TOKEN
EOF
    chmod 600 /root/.netrc

    # 6. Create Artifactory repos as OVERLAY (Keep original repos)
    # -------------------------------------------------------------------------
    echo "Adding Artifactory repository overlay (priority=1)..."

    if command -v dnf >/dev/null 2>&1; then
        PKG_MGR="dnf"
    else
        PKG_MGR="yum"
    fi

    # Create Artifactory repo configuration with highest priority (priority=1)
    # Use "00-" prefix so it sorts first alphabetically
    cat > /etc/yum.repos.d/00-artifactory-baseos.repo <<EOF
[artifactory-baseos]
name=Artifactory - Rocky Linux $VERSION - BaseOS
baseurl=${BASEOS_URL}
enabled=1
gpgcheck=0
priority=1
username=$ARTIFACTORY_USER
password=$ARTIFACTORY_TOKEN
EOF

    cat > /etc/yum.repos.d/00-artifactory-appstream.repo <<EOF
[artifactory-appstream]
name=Artifactory - Rocky Linux $VERSION - AppStream
baseurl=${APPSTREAM_URL}
enabled=1
gpgcheck=0
priority=1
username=$ARTIFACTORY_USER
password=$ARTIFACTORY_TOKEN
EOF

    cat > /etc/yum.repos.d/00-artifactory-extras.repo <<EOF
[artifactory-extras]
name=Artifactory - Rocky Linux $VERSION - Extras
baseurl=${EXTRAS_URL}
enabled=1
gpgcheck=0
priority=1
username=$ARTIFACTORY_USER
password=$ARTIFACTORY_TOKEN
EOF

    cat > /etc/yum.repos.d/00-artifactory-epel.repo <<EOF
[artifactory-epel]
name=Artifactory - EPEL $VERSION
baseurl=${EPEL_URL}
enabled=1
gpgcheck=0
priority=1
username=$ARTIFACTORY_USER
password=$ARTIFACTORY_TOKEN
EOF

    echo "Artifactory YUM/DNF configuration complete (overlay mode - original repos preserved)."

    # 7. Verify configuration
    # -------------------------------------------------------------------------
    echo "Testing repository access..."
    $PKG_MGR repolist || echo "Warning: Repository list failed. Check credentials and URLs."
}

# EXECUTION GUARD
setup_artifactory_mirror "$1" "$2" "$3"
