#!/bin/bash

rm -rf build/packages
mkdir -p build/packages
# copy required packages
cp ../../src/packages/build/ubuntu24.04/xroad-base* build/packages
cp ../../src/packages/build/ubuntu24.04/xroad-signer* build/packages
cp ../../src/packages/build/ubuntu24.04/xroad-nginx* build/packages
cp ../../src/packages/build/ubuntu24.04/xroad-confproxy* build/packages
cp ../../src/packages/build/ubuntu24.04/xroad-configuration-service* build/packages