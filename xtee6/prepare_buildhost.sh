#!/bin/bash

set -e

sudo apt-get install -y curl software-properties-common
sudo apt-add-repository -y ppa:openjdk-r/ppa
sudo apt-get update


sudo apt-get install -y openjdk-7-jre-headless
sudo apt-get install -y openjdk-8-jdk  build-essential git unzip maven debhelper
sudo apt-get remove -y openjdk-7-jre-headless

cd ~

wget https://services.gradle.org/distributions/gradle-2.1-bin.zip -O gradle-2.1-bin.zip
unzip gradle-2.1-bin.zip

gpg --keyserver hkp://keys.gnupg.net --recv-keys 409B6B1796C275462A1703113804BB82D39DC0E3
curl -L https://get.rvm.io | bash -s stable --ruby
source ~/.rvm/scripts/rvm
rvm install jruby
rvm use jruby
jgem install bundle warbler

