#!/bin/bash -e
cd ~/xroad/src || { echo "X-Road source missing"; exit 1; }
JRUBY_VERSION=$(cat .jruby-version)
./update_ruby_dependencies.sh
rvm "jruby-$JRUBY_VERSION" do ./gradlew buildAll runProxyTest
packages/build-deb.sh bionic
cd packages/build/ubuntu18.04/ && dpkg-scanpackages . | gzip > Packages.gz
