#!/bin/bash

# OpenBao APT Repository Cleanup (Container Side)
#
# Removes the OpenBao APT repository configuration written by configure-mirror-openbao-deb.sh.
#
# Usage:
# ./cleanup-mirror-openbao-deb.sh

cleanup_openbao_deb() {
    echo "Removing OpenBao APT repository configuration..."

    rm -f /etc/apt/sources.list.d/openbao.list
    rm -f /usr/share/keyrings/openbao-keyring.asc

    echo "OpenBao APT repository configuration removed."
}

cleanup_openbao_deb
