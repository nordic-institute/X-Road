#!/bin/bash

./init_image/build-initializer.sh

echo "Building serverconf-init"
docker build --tag xroad-ss-serverconf-init \
--build-arg CHANGELOG_FILE=serverconf-changelog.xml \
--build-context changelog=$XROAD_HOME/src/security-server/admin-service/infra-jpa/src/main/resources/liquibase/ \
init_db/

echo "Building messagelog-init"
docker build --tag xroad-ss-messagelog-init \
--build-arg CHANGELOG_FILE=messagelog-changelog.xml \
--build-context changelog=$XROAD_HOME/src/packages/src/xroad/common/addon/proxy/ \
init_db/

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
