#!/bin/bash

# Artifactory APT Cleanup (Container Side)
#
# Removes Artifactory APT overlay configuration.
# Original public sources remain intact since they were never deleted.
#
# Usage:
# ./cleanup-artifactory-apt.sh

cleanup_artifactory_apt() {
    echo "Removing Artifactory APT configuration..."

    # Remove Artifactory source file
    rm -f /etc/apt/sources.list.d/00-artifactory.sources

    # Remove priority pinning
    rm -f /etc/apt/preferences.d/00-artifactory

    # Remove authentication configuration
    rm -f /etc/apt/auth.conf.d/artifactory.conf

    # Remove CA certificate
    rm -f /usr/local/share/ca-certificates/artifactory.crt
    rm -f /etc/apt/apt.conf.d/99artifactory-cert

    # Update CA certificates store
    if command -v update-ca-certificates >/dev/null 2>&1; then
        update-ca-certificates 2>/dev/null || true
    fi

    echo "Artifactory cleanup complete. Using default public mirrors."
}

cleanup_artifactory_apt
