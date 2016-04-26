#!/bin/bash
set -e

sudo apt-get install -y curl software-properties-common
sudo apt-add-repository -y ppa:openjdk-r/ppa
sudo apt-get update

sudo apt-get install -y openjdk-8-jdk build-essential git unzip debhelper

cd ~

wget https://services.gradle.org/distributions/gradle-2.4-bin.zip -O gradle-2.4-bin.zip
unzip gradle-2.4-bin.zip

gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys 409B6B1796C275462A1703113804BB82D39DC0E3
curl -L https://get.rvm.io | bash -s stable 
source ~/.rvm/scripts/rvm
rvm install jruby-1.7.22 --binary
rvm use jruby-1.7.22
jgem install bundle warbler:1.4.9

mkdir -p /var/tmp/xroad

