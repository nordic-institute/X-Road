#!/bin/bash

# Mirror APT Cleanup (Container Side)
#
# Removes mirror APT overlay configuration.
# Original public sources remain intact since they were never deleted.
#
# Usage:
# ./cleanup-mirror-apt.sh

cleanup_mirror_apt() {
    echo "Removing mirror APT configuration..."

    # Remove mirror source file
    rm -f /etc/apt/sources.list.d/00-mirror.sources

    # Remove priority pinning
    rm -f /etc/apt/preferences.d/00-mirror

    # Remove authentication configuration
    rm -f /etc/apt/auth.conf.d/mirror.conf

    # Remove TLS verification override
    rm -f /etc/apt/apt.conf.d/99mirror-no-verify

    echo "Mirror cleanup complete. Using default public mirrors."
}

cleanup_mirror_apt
