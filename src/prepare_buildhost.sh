#!/bin/bash
test "$(lsb_release -si)" == "Ubuntu" || { echo "This script supports only Ubuntu"; exit 1; }
set -e

sudo apt-get update
sudo apt-get install -y curl software-properties-common

REL=$(lsb_release -sr | cut -d'.' -f1)
JRUBY_VERSION=$(cat .jruby-version || echo "9.1.17.0")

if [ $REL -lt 16 ]; then
    sudo apt-add-repository -y ppa:openjdk-r/ppa
    sudo apt-get update
fi

sudo apt-get install -y openjdk-8-jdk-headless build-essential git unzip debhelper devscripts
sudo update-ca-certificates -f

