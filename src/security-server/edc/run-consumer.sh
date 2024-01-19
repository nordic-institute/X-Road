#!/bin/bash

resourcesDir="src/main/resources"
java -Dedc.keystore="$resourcesDir/certs/cert.pfx" \
-Dedc.keystore.password=123456 \
-Dedc.vault="$resourcesDir/configuration/consumer-vault.properties" \
-Dedc.fs.config="$resourcesDir/configuration/consumer-configuration.properties" \
-Xmx128m \
-jar build/libs/connector.jar
