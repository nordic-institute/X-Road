#!/bin/bash

rm -rf ./build/packages
mkdir -p ./build/packages
mkdir -p ./build/libs

# Should match opentelemetry annotations version.
if [ ! -f ./build/libs/opentelemetry-javaagent.jar ]; then
  curl -L -o ./build/libs/opentelemetry-javaagent.jar \
  https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.9.0/opentelemetry-javaagent.jar
else
  echo "opentelemetry-javaagent.jar already exists. Skipping download."
fi
