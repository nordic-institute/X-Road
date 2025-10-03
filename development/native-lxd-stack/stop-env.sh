#!/bin/bash
set -e
source "${BASH_SOURCE%/*}/../../.scripts/base-script.sh"

if limactl list | grep -q '^xroad-lxd'; then
    # Check current status
    current_status=$(limactl list | grep '^xroad-lxd' | awk '{print $2}')
    
    if [ "$current_status" = "Stopped" ]; then
        log_info "Lima instance xroad-lxd is already stopped"
    elif [ "$current_status" = "Running" ]; then
        log_info "Stopping lima instance xroad-lxd"
        limactl stop xroad-lxd
        
        # Verify that the instance is stopped
        if limactl list | grep '^xroad-lxd' | awk '{print $2}' | grep -q 'Stopped'; then
            log_info "Lima instance xroad-lxd stopped successfully"
        else
            log_error "Failed to stop lima instance xroad-lxd"
            exit 1
        fi
    else
        log_info "Lima instance xroad-lxd has status: $current_status"
    fi
else
    log_info "Lima instance xroad-lxd not found"
fi