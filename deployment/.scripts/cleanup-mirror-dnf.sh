#!/bin/bash

# Mirror DNF Cleanup (Container Side)
#
# Removes mirror DNF overlay configuration.
# Original public repos remain intact since they were never deleted.
#
# Usage:
# ./cleanup-mirror-dnf.sh

cleanup_mirror_dnf() {
    echo "Removing mirror DNF configuration..."

    # Remove mirror repo files
    rm -f /etc/yum.repos.d/00-mirror-*.repo

    # Remove authentication configuration
    rm -f /root/.netrc

    echo "Mirror cleanup complete. Using default public mirrors."
}

cleanup_mirror_dnf
