#!/bin/bash

# Mirror DNF Configuration (Container Side)
#
# Configures DNF to use a package mirror as a HIGH-PRIORITY OVERLAY.
# Original repos are preserved - mirror is added with priority=1.
# Handles authentication and dynamic version detection. TLS verification is disabled
# (GPG signature verification still protects package integrity).
#
# Usage:
# ./configure-mirror-dnf.sh <BASE_URL> <USER>
#
# Arguments:
# BASE_URL - Mirror base URL (e.g., https://artifactory.niis.org/artifactory)
# USER - Username for authentication
#
# Token is read from /run/secrets/mirror_token (Docker) or XROAD_MIRROR_TOKEN env var (Ansible)

setup_mirror() {
    echo "Starting Mirror DNF Configuration..."

    # 1. Resolve Arguments and Credentials
    # -------------------------------------------------------------------------
    MIRROR_BASE_URL="$1"
    MIRROR_USER="$2"

    # Token from Docker secret or environment variable
    if [ -f /run/secrets/mirror_token ]; then
        MIRROR_TOKEN=$(cat /run/secrets/mirror_token)
    elif [ -n "$XROAD_MIRROR_TOKEN" ]; then
        MIRROR_TOKEN="$XROAD_MIRROR_TOKEN"
    fi

    if [ -z "$MIRROR_BASE_URL" ] || [ -z "$MIRROR_USER" ] || [ -z "$MIRROR_TOKEN" ]; then
        echo "Mirror credentials (BASE_URL, USER, TOKEN) not fully present. Skipping mirror setup."
        echo "Using default public mirrors."
        return 0
    fi

    echo "Configuring package mirror: $MIRROR_BASE_URL"

    # 2. Detect Distribution, Version and Architecture
    # -------------------------------------------------------------------------
    VERSION=""
    ARCH=$(uname -m)

    if [ -f /etc/os-release ]; then
        . /etc/os-release
        VERSION="${VERSION_ID%%.*}"  # Major version only (9 or 10)
    else
        echo "/etc/os-release not found. Cannot detect distribution."
        return 1
    fi

    # 3. Build Repository URLs
    # -------------------------------------------------------------------------
    MIRROR_HOST=$(echo "$MIRROR_BASE_URL" | sed -e 's|^[^/]*//||' -e 's|/.*$||')

    # Mirrors the full upstream path structure:
    # mirror-rocky -> https://dl.rockylinux.org/pub/rocky/
    # mirror-oracle -> https://yum.oracle.com/
    # mirror-epel -> https://dl.fedoraproject.org/pub/epel/
    case "$ID" in
        rocky)
            DISTRO_NAME="Rocky Linux"
            BASEOS_URL="${MIRROR_BASE_URL}/mirror-rocky/${VERSION}/BaseOS/${ARCH}/os/"
            APPSTREAM_URL="${MIRROR_BASE_URL}/mirror-rocky/${VERSION}/AppStream/${ARCH}/os/"
            EXTRAS_URL="${MIRROR_BASE_URL}/mirror-rocky/${VERSION}/extras/${ARCH}/os/"
            DISTRO_GPG_KEY_FILE="RPM-GPG-KEY-Rocky-${VERSION}"
            DISTRO_GPG_KEY_URL="${MIRROR_BASE_URL}/mirror-rocky/RPM-GPG-KEY-Rocky-${VERSION}"
            ;;
        ol)
            DISTRO_NAME="Oracle Linux"
            BASEOS_URL="${MIRROR_BASE_URL}/mirror-oracle/repo/OracleLinux/OL${VERSION}/baseos/latest/${ARCH}/"
            APPSTREAM_URL="${MIRROR_BASE_URL}/mirror-oracle/repo/OracleLinux/OL${VERSION}/appstream/${ARCH}/"
            EXTRAS_URL=""
            DISTRO_GPG_KEY_FILE="RPM-GPG-KEY-oracle"
            DISTRO_GPG_KEY_URL="${MIRROR_BASE_URL}/mirror-oracle/RPM-GPG-KEY-oracle-ol${VERSION}"
            ;;
        *)
            echo "Unsupported distribution: $ID. Only Rocky Linux and Oracle Linux are supported. Skipping mirror setup."
            return 0
            ;;
    esac

    echo "Detected: $DISTRO_NAME $VERSION ($ARCH)"

    EPEL_URL="${MIRROR_BASE_URL}/mirror-epel/${VERSION}/Everything/${ARCH}/"

    # GPG key paths
    DISTRO_GPG_KEY="file:///etc/pki/rpm-gpg/${DISTRO_GPG_KEY_FILE}"
    EPEL_GPG_KEY="file:///etc/pki/rpm-gpg/RPM-GPG-KEY-EPEL-${VERSION}"

    # 4. Configure Authentication
    # -------------------------------------------------------------------------
    cat > /root/.netrc <<EOF
machine $MIRROR_HOST
login $MIRROR_USER
password $MIRROR_TOKEN
EOF
    chmod 600 /root/.netrc

    # 5. Import GPG keys not present in minimal container images
    # -------------------------------------------------------------------------
    mkdir -p /etc/pki/rpm-gpg

    # Distribution GPG key
    if [ ! -f "/etc/pki/rpm-gpg/${DISTRO_GPG_KEY_FILE}" ]; then
        echo "Importing $DISTRO_NAME GPG key from mirror..."
        if curl -fsSL --netrc -o "/etc/pki/rpm-gpg/${DISTRO_GPG_KEY_FILE}" "$DISTRO_GPG_KEY_URL"; then
            rpm --import "/etc/pki/rpm-gpg/${DISTRO_GPG_KEY_FILE}"
        else
            echo "Warning: Failed to fetch $DISTRO_NAME GPG key. Package installation may fail."
        fi
    fi

    # EPEL GPG key
    EPEL_KEY_URL="${MIRROR_BASE_URL}/mirror-epel/RPM-GPG-KEY-EPEL-${VERSION}"
    echo "Importing EPEL GPG key from mirror..."
    if curl -fsSL --netrc -o /tmp/RPM-GPG-KEY-EPEL-${VERSION} "$EPEL_KEY_URL"; then
        rpm --import /tmp/RPM-GPG-KEY-EPEL-${VERSION}
        rm -f /tmp/RPM-GPG-KEY-EPEL-${VERSION}
    else
        echo "Warning: Failed to fetch EPEL GPG key. EPEL packages may not install."
    fi

    # 6. Create mirror repos as OVERLAY (Keep original repos)
    # -------------------------------------------------------------------------
    echo "Adding mirror repository overlay (priority=1)..."

    # Create mirror repo configuration with highest priority (priority=1)
    # Use "00-" prefix so it sorts first alphabetically
    cat > /etc/yum.repos.d/00-mirror-baseos.repo <<EOF
[mirror-baseos]
name=Mirror - $DISTRO_NAME $VERSION - BaseOS
baseurl=${BASEOS_URL}
enabled=1
gpgcheck=1
gpgkey=${DISTRO_GPG_KEY}
sslverify=0
priority=1
username=$MIRROR_USER
password=$MIRROR_TOKEN
EOF

    cat > /etc/yum.repos.d/00-mirror-appstream.repo <<EOF
[mirror-appstream]
name=Mirror - $DISTRO_NAME $VERSION - AppStream
baseurl=${APPSTREAM_URL}
enabled=1
gpgcheck=1
gpgkey=${DISTRO_GPG_KEY}
sslverify=0
priority=1
username=$MIRROR_USER
password=$MIRROR_TOKEN
EOF

    if [ -n "$EXTRAS_URL" ]; then
        cat > /etc/yum.repos.d/00-mirror-extras.repo <<EOF
[mirror-extras]
name=Mirror - $DISTRO_NAME $VERSION - Extras
baseurl=${EXTRAS_URL}
enabled=1
gpgcheck=1
gpgkey=${DISTRO_GPG_KEY}
sslverify=0
priority=1
username=$MIRROR_USER
password=$MIRROR_TOKEN
EOF
    fi

    cat > /etc/yum.repos.d/00-mirror-epel.repo <<EOF
[mirror-epel]
name=Mirror - EPEL $VERSION
baseurl=${EPEL_URL}
enabled=1
gpgcheck=1
gpgkey=${EPEL_GPG_KEY}
sslverify=0
priority=1
username=$MIRROR_USER
password=$MIRROR_TOKEN
EOF

    echo "Mirror DNF configuration complete (overlay mode - original repos preserved)."

    # 7. Verify configuration
    # -------------------------------------------------------------------------
    echo "Testing repository access..."
    dnf repolist || echo "Warning: Repository list failed. Check credentials and URLs."
}

# EXECUTION GUARD
setup_mirror "$1" "$2"