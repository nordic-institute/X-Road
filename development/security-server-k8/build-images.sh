#!/bin/bash

./init_image/build-initializer.sh

echo "Building config-server"
docker build --tag xroad-ss-config \
--file build-context/Dockerfile \
--build-context entrypoint-ctx=build-context/entrypoint \
--build-arg JAR_FILE=build/libs/*.jar \
--build-arg ENTRYPOINT_SCRIPT=entrypoint.sh \
$XROAD_HOME/src/security-server/configuration-service/application

echo "Building confclient"
docker build --tag xroad-ss-confclient \
--file build-context/Dockerfile \
--build-context entrypoint-ctx=build-context/entrypoint \
--build-arg JAR_FILE=build/libs/*.jar \
--build-arg ENTRYPOINT_SCRIPT=entrypoint.sh \
$XROAD_HOME/src/configuration-client/application


echo "Building signer"
docker build --tag xroad-ss-signer \
--file build-context/Dockerfile \
--build-context entrypoint-ctx=build-context/entrypoint \
--build-arg JAR_FILE=build/libs/*.jar \
--build-arg ENTRYPOINT_SCRIPT=entrypoint.sh \
$XROAD_HOME/src/signer/application/

echo "Building proxy"
docker build --tag xroad-ss-proxy \
--file build-context/Dockerfile \
--build-context entrypoint-ctx=build-context/entrypoint \
--build-arg JAR_FILE=build/libs/*.jar \
--build-arg ENTRYPOINT_SCRIPT=entrypoint-proxy.sh \
$XROAD_HOME/src/proxy/application/

echo "Building ui"
docker build --tag xroad-ss-ui \
--file build-context/Dockerfile \
--build-context entrypoint-ctx=build-context/entrypoint \
--build-arg JAR_FILE=build/libs/*.jar \
--build-arg ENTRYPOINT_SCRIPT=entrypoint.sh \
$XROAD_HOME/src/security-server/admin-service/application
