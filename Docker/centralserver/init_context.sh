#!/bin/bash

rm -rf ./build
mkdir -p ./build/packages
mkdir -p ./build/libs

cp ../../src/central-server/ds-catalog-service/build/libs/ds-catalog-service.jar ./build/libs
cp ../../src/security-server/edc/runtime/identity-hub/build/libs/edc-identity-hub-1.0.jar ./build/libs
