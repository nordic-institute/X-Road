#!/bin/bash

# OpenBao DNF Repository Cleanup (Container Side)
#
# Removes the OpenBao DNF repository configuration written by configure-mirror-openbao-rpm.sh.
#
# Usage:
# ./cleanup-mirror-openbao-rpm.sh

cleanup_openbao_rpm() {
    echo "Removing OpenBao DNF repository configuration..."

    rm -f /etc/yum.repos.d/openbao.repo
    rm -f /etc/pki/rpm-gpg/openbao-gpg-pub-20240618.asc

    echo "OpenBao DNF repository configuration removed."
}

cleanup_openbao_rpm
