#!/bin/bash

# Configuration
VM_NAME="xroad-lxd"
MEMORY="10GiB"
LXD_PORT=28443
TRUST_PASSWORD="secret"
RECREATE=false

function check_macos() {
    if [[ $(uname) != "Darwin" ]]; then
        echo "This script only works on MacOS"
        exit 1
    fi
}

function check_prerequisites() {
    # Check for brew
    if ! command -v brew &> /dev/null; then
        echo "Homebrew is not installed"
        exit 1
    fi

    # Check for lima
    if ! command -v limactl &> /dev/null; then
        echo "Installing lima..."
        brew install lima
    fi

    # Check for lxc
    if ! command -v lxc &> /dev/null; then
        echo "Installing lxc..."
        brew install lxc
    fi
}

function handle_existing_vm() {
    if limactl list | grep -q "$VM_NAME"; then
        if [ "$RECREATE" = true ]; then
            echo "Removing existing VM $VM_NAME..."
            limactl stop $VM_NAME
            limactl delete $VM_NAME
        else
            echo "VM $VM_NAME already exists"
        fi
    fi
}

function create_and_start_vm() {
    echo "Creating and starting Lima VM $VM_NAME..."
    limactl start --name=${VM_NAME} --tty=false templates/lima.yaml

    # Check if VM exists and is running
    if ! limactl list | grep -q "$VM_NAME"; then
        echo "Failed to start VM $VM_NAME"
        exit 1
    fi
}

function setup_ssh_config() {
    echo "Enabling ssh.config access..."
    chmod 644 ~/.lima/xroad-lxd/ssh.config
}

function parse_arguments() {
    while [[ "$#" -gt 0 ]]; do
        case $1 in
            --recreate) RECREATE=true ;;
            *) echo "Unknown parameter: $1"; exit 1 ;;
        esac
        shift
    done
}

function main() {
    parse_arguments "$@"
    check_macos
    check_prerequisites
    handle_existing_vm
    create_and_start_vm
    setup_ssh_config

    echo "Proceeding with LXD setup..."
    source ./utils/setup-lxc.sh
}

# Execute main function with all script arguments
main "$@"
