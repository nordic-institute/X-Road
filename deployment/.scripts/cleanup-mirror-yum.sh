#!/bin/bash

# Mirror YUM/DNF Cleanup (Container Side)
#
# Removes mirror YUM/DNF overlay configuration.
# Original public repos remain intact since they were never deleted.
#
# Usage:
# ./cleanup-mirror-yum.sh

cleanup_mirror_yum() {
    echo "Removing mirror YUM/DNF configuration..."

    # Remove mirror repo files
    rm -f /etc/yum.repos.d/00-mirror-*.repo

    # Remove authentication configuration
    rm -f /root/.netrc

    echo "Mirror cleanup complete. Using default public mirrors."
}

cleanup_mirror_yum
