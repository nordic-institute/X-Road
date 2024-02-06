#!/bin/bash

resourcesDir="src/main/resources"
jarFile="connector.jar"
debugPort=""

# Check for --in-memory and --debug flags
for arg in "$@"
do
    if [ "$arg" == "--in-memory" ]; then
        jarFile="connector-inmemory.jar"
    elif [ "$arg" == "--debug" ]; then
        debugPort="5005"
    fi
done

# Construct the Java command
javaCmd="java -Dedc.keystore="$resourcesDir/certs/cert.pfx" \
-Dedc.keystore.password=123456 \
-Dedc.vault="$resourcesDir/configuration/provider-vault.properties" \
-Dedc.fs.config="$resourcesDir/configuration/provider-configuration.properties" \
-Dxroad.common.grpc-internal-tls-enabled=false \
-Xmx128m"

# Add debug options if debug port is set
if [ ! -z "$debugPort" ]; then
    javaCmd="$javaCmd -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:$debugPort"
fi

# Append -jar option and execute the command
javaCmd="$javaCmd -jar "build/libs/$jarFile""
exec $javaCmd
