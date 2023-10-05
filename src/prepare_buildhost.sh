#!/bin/bash
test "$(lsb_release -si)" == "Ubuntu" || { echo "This script supports only Ubuntu"; exit 1; }
set -e

sudo apt-get update
sudo apt-get install -y curl software-properties-common

REL=$(lsb_release -sr | cut -d'.' -f1)

if [ $REL -lt 20 ]; then
    sudo apt-add-repository -y ppa:openjdk-r/ppa
    sudo apt-get update
fi

sudo apt-get install -y openjdk-17-jdk-headless build-essential git unzip debhelper devscripts
sudo update-ca-certificates -f

mkdir -p /var/tmp/xroad

if [[ $REL -ge 20 && ! -e /.dockerenv ]]; then
    if ! command -v docker &>/dev/null; then
        echo "Install docker"
        sudo apt-get install -y docker.io
        sudo addgroup $(whoami) docker
        newgrp docker
    fi
fi
