#!/bin/bash

rm -rf ./build
mkdir -p ./build/packages
mkdir -p ./build/libs

cp ../../src/central-server/ds-catalog-service/build/libs/ds-catalog-service.jar ./build/libs
cp ../../src/security-server/edc/runtime/identity-hub/build/libs/edc-identity-hub-1.0.jar ./build/libs
cp ../../src/central-server/ds-credential-service/build/libs/ds-credential-service.jar ./build/libs

cp -r ../../src/security-server/edc/runtime/identity-hub/src/main/resources/liquibase ./build
