#!/bin/bash

rm -rf ./build/packages
mkdir -p ./build/packages
mkdir -p ./build/libs

cp ../../src/security-server/edc/runtime/control-plane/build/libs/edc-control-plane.jar ./build/libs
cp ../../src/security-server/edc/runtime/data-plane/build/libs/edc-data-plane.jar ./build/libs
cp ../../src/security-server/edc/runtime/identity-hub/build/libs/edc-identity-hub.jar ./build/libs

cp -r ../../src/security-server/edc/runtime/control-plane/src/main/resources/liquibase ./build
cp -r ../../src/security-server/edc/runtime/data-plane/src/main/resources/liquibase ./build
cp -r ../../src/security-server/edc/runtime/identity-hub/src/main/resources/liquibase ./build

# Should match opentelemetry annotations version.
if [ ! -f ./build/libs/opentelemetry-javaagent.jar ]; then
  wget -O ./build/libs/opentelemetry-javaagent.jar \
  https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.9.0/opentelemetry-javaagent.jar
else
  echo "opentelemetry-javaagent.jar already exists. Skipping download."
fi