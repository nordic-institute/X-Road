#!/bin/bash

rm -rf ./build
mkdir -p ./build/packages

cp ../../src/security-server/edc/build/libs/connector.jar ./build
cp -r ../../src/security-server/edc/src/main/resources/liquibase ./build
