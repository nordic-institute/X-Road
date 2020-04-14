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

cd ~

if ! command -v rvm &>/dev/null; then
    gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 409B6B1796C275462A1703113804BB82D39DC0E3 7D2BAF1CF37B13E2069D6956105BD0E739499BDB
    curl -L https://get.rvm.io | bash -s stable
    source ~/.rvm/scripts/rvm
fi

rvm install jruby-$JRUBY_VERSION --binary --skip-gemsets
rvm jruby-$JRUBY_VERSION do jgem install jruby-openssl jruby-launcher gem-wrappers rubygems-bundler rake:13.0.1 rvm jruby-jars:$JRUBY_VERSION bundler:1.14.6 warbler:2.0.4 bundler-audit

mkdir -p /var/tmp/xroad

if [[ $REL -ge 16 && ! -e /.dockerenv ]]; then
    if ! command -v docker &>/dev/null; then
        echo "Install docker"
        sudo apt-get install -y docker.io
        sudo addgroup $(whoami) docker
        newgrp docker
    fi
fi

