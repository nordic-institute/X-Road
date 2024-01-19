#!/bin/bash

resourcesDir="src/main/resources"
exec java -Dedc.keystore="$resourcesDir/certs/cert.pfx" \
-Dedc.keystore.password=123456 \
-Dedc.vault="$resourcesDir/configuration/provider-vault.properties" \
-Dedc.fs.config="$resourcesDir/configuration/provider-configuration.properties" \
-Xmx128m \
-jar build/libs/connector.jar
