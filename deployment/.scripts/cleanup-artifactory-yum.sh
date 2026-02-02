#!/bin/bash

# Artifactory YUM/DNF Cleanup (Container Side)
#
# Removes Artifactory YUM/DNF overlay configuration.
# Original public repos remain intact since they were never deleted.
#
# Usage:
# ./cleanup-artifactory-yum.sh

cleanup_artifactory_yum() {
    echo "Removing Artifactory YUM/DNF configuration..."

    # Remove Artifactory repo files
    rm -f /etc/yum.repos.d/00-artifactory-*.repo

    # Remove authentication configuration
    rm -f /root/.netrc

    # Remove CA certificate
    rm -f /etc/pki/ca-trust/source/anchors/artifactory.crt

    # Update CA trust store
    if command -v update-ca-trust >/dev/null 2>&1; then
        update-ca-trust extract 2>/dev/null || true
    fi

    echo "Artifactory cleanup complete. Using default public mirrors."
}

cleanup_artifactory_yum
