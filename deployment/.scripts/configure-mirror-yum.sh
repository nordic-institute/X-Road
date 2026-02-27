#!/bin/bash

# Mirror YUM/DNF Configuration (Container Side)
#
# Configures YUM/DNF to use a package mirror as a HIGH-PRIORITY OVERLAY.
# Original repos are preserved - mirror is added with priority=1.
# Handles authentication and dynamic version detection. TLS verification is disabled
# (GPG signature verification still protects package integrity).
#
# Usage:
#   ./configure-mirror-yum.sh <BASE_URL> <USER>
#
# Arguments:
#   BASE_URL - Mirror base URL (e.g., https://artifactory.niis.org/artifactory)
#   USER     - Username for authentication
#
# Token is read from /run/secrets/mirror_token (Docker) or XROAD_MIRROR_TOKEN env var (Ansible)

setup_mirror() {
    echo "Starting Mirror YUM/DNF Configuration..."

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

    # 2. Detect Rocky Linux Version and Architecture
    # -------------------------------------------------------------------------
    VERSION=""
    ARCH=$(uname -m)

    if [ -f /etc/os-release ]; then
        . /etc/os-release
        if [ "$ID" != "rocky" ]; then
            echo "Unsupported distribution: $ID. Only Rocky Linux is supported. Skipping mirror setup."
            return 0
        fi
        VERSION="${VERSION_ID%%.*}"  # Major version only (8 or 9)
    else
        echo "/etc/os-release not found. Cannot detect distribution."
        return 1
    fi

    echo "Detected: Rocky Linux $VERSION ($ARCH)"

    # 3. Build Repository URLs
    # -------------------------------------------------------------------------
    MIRROR_HOST=$(echo "$MIRROR_BASE_URL" | sed -e 's|^[^/]*//||' -e 's|/.*$||')
    # Mirrors the full upstream path structure:
    #   mirror-rocky -> https://dl.rockylinux.org/pub/rocky/
    #   mirror-epel  -> https://dl.fedoraproject.org/pub/epel/
    BASEOS_URL="${MIRROR_BASE_URL}/mirror-rocky/${VERSION}/BaseOS/${ARCH}/os/"
    APPSTREAM_URL="${MIRROR_BASE_URL}/mirror-rocky/${VERSION}/AppStream/${ARCH}/os/"
    EXTRAS_URL="${MIRROR_BASE_URL}/mirror-rocky/${VERSION}/extras/${ARCH}/os/"
    EPEL_URL="${MIRROR_BASE_URL}/mirror-epel/${VERSION}/Everything/${ARCH}/"

    # GPG key paths
    ROCKY_GPG_KEY="file:///etc/pki/rpm-gpg/RPM-GPG-KEY-Rocky-${VERSION}"
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

    # Rocky Linux GPG key
    if [ ! -f "/etc/pki/rpm-gpg/RPM-GPG-KEY-Rocky-${VERSION}" ]; then
        ROCKY_KEY_URL="${MIRROR_BASE_URL}/mirror-rocky/RPM-GPG-KEY-Rocky-${VERSION}"
        echo "Importing Rocky Linux GPG key from mirror..."
        if curl -fsSL --netrc -o "/etc/pki/rpm-gpg/RPM-GPG-KEY-Rocky-${VERSION}" "$ROCKY_KEY_URL"; then
            rpm --import "/etc/pki/rpm-gpg/RPM-GPG-KEY-Rocky-${VERSION}"
        else
            echo "Warning: Failed to fetch Rocky Linux GPG key. Package installation may fail."
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

    if command -v dnf >/dev/null 2>&1; then
        PKG_MGR="dnf"
    else
        PKG_MGR="yum"
    fi

    # Create mirror repo configuration with highest priority (priority=1)
    # Use "00-" prefix so it sorts first alphabetically
    cat > /etc/yum.repos.d/00-mirror-baseos.repo <<EOF
[mirror-baseos]
name=Mirror - Rocky Linux $VERSION - BaseOS
baseurl=${BASEOS_URL}
enabled=1
gpgcheck=1
gpgkey=${ROCKY_GPG_KEY}
sslverify=0
priority=1
username=$MIRROR_USER
password=$MIRROR_TOKEN
EOF

    cat > /etc/yum.repos.d/00-mirror-appstream.repo <<EOF
[mirror-appstream]
name=Mirror - Rocky Linux $VERSION - AppStream
baseurl=${APPSTREAM_URL}
enabled=1
gpgcheck=1
gpgkey=${ROCKY_GPG_KEY}
sslverify=0
priority=1
username=$MIRROR_USER
password=$MIRROR_TOKEN
EOF

    cat > /etc/yum.repos.d/00-mirror-extras.repo <<EOF
[mirror-extras]
name=Mirror - Rocky Linux $VERSION - Extras
baseurl=${EXTRAS_URL}
enabled=1
gpgcheck=1
gpgkey=${ROCKY_GPG_KEY}
sslverify=0
priority=1
username=$MIRROR_USER
password=$MIRROR_TOKEN
EOF

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

    echo "Mirror YUM/DNF configuration complete (overlay mode - original repos preserved)."

    # 7. Verify configuration
    # -------------------------------------------------------------------------
    echo "Testing repository access..."
    $PKG_MGR repolist || echo "Warning: Repository list failed. Check credentials and URLs."
}

# EXECUTION GUARD
setup_mirror "$1" "$2"
