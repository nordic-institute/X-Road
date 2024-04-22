#!/bin/bash

rm -rf ./build
mkdir -p ./build/packages
mkdir -p ./build/libs

cp ../../src/security-server/edc/runtime/connector/build/libs/edc-connector.jar ./build/libs
cp ../../src/security-server/edc/runtime/identity-hub/build/libs/edc-identity-hub.jar ./build/libs

cp -r ../../src/security-server/edc/runtime/connector/src/main/resources/liquibase ./build
