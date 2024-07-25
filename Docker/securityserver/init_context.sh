#!/bin/bash

rm -rf ./build
mkdir -p ./build/packages
mkdir -p ./build/libs

cp ../../src/security-server/edc/runtime/connector/build/libs/edc-connector.jar ./build/libs
cp ../../src/security-server/edc/runtime/identity-hub/build/libs/edc-identity-hub.jar ./build/libs

cp -r ../../src/security-server/edc/runtime/connector/src/main/resources/liquibase ./build

# Should match opentelemetry annotations version.
wget -O ./build/libs/opentelemetry-javaagent.jar \
https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.6.0/opentelemetry-javaagent.jar