#!/bin/bash

resourcesDir="src/main/resources"
jarFile="build/libs/connector.jar"

# Check for --in-memory flag
for arg in "$@"
do
    if [ "$arg" == "--in-memory" ]; then
        jarFile="connector-inmemory.jar"
        break
    fi
done

java -Dedc.keystore="$resourcesDir/certs/cert.pfx" \
-Dedc.keystore.password=123456 \
-Dedc.vault="$resourcesDir/configuration/consumer-vault.properties" \
-Dedc.fs.config="$resourcesDir/configuration/consumer-configuration.properties" \
-Xmx128m \
-jar "build/libs/$jarFile"
