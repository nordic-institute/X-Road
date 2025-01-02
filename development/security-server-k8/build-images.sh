#!/bin/bash

echo "Building init-runner"
docker build --tag init-runner init_image/

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

echo "Building baseline"
docker build --tag xroad-ss-baseline-runtime \
--file build-context/Dockerfile-baseline \
$XROAD_HOME/src/configuration-client/application


echo "Building confclient"
docker build --tag xroad-ss-confclient \
--file build-context/Dockerfile \
--build-context entrypoint-ctx=build-context/entrypoint \
--build-context service-ctx=build-context/service-context/confclient \
--build-arg JAR_FILE=build/libs/*.jar \
--build-arg ENTRYPOINT_SCRIPT=entrypoint.sh \
$XROAD_HOME/src/configuration-client/application


echo "Building signer"
docker build --tag xroad-ss-signer \
--file build-context/Dockerfile \
--build-context entrypoint-ctx=build-context/entrypoint \
--build-context service-ctx=build-context/service-context/signer \
--build-arg JAR_FILE=build/libs/*.jar \
--build-arg ENTRYPOINT_SCRIPT=entrypoint.sh \
$XROAD_HOME/src/signer/application/

echo "Building proxy"
docker build --tag xroad-ss-proxy \
--file build-context/Dockerfile \
--build-context entrypoint-ctx=build-context/entrypoint \
--build-context service-ctx=build-context/service-context/proxy \
--build-arg JAR_FILE=build/libs/*.jar \
--build-arg ENTRYPOINT_SCRIPT=entrypoint-proxy.sh \
$XROAD_HOME/src/proxy/application/

echo "Building ui"
docker build --tag xroad-ss-ui \
--file build-context/Dockerfile \
--build-context entrypoint-ctx=build-context/entrypoint \
--build-context service-ctx=build-context/service-context/ui \
--build-arg JAR_FILE=build/libs/*.jar \
--build-arg ENTRYPOINT_SCRIPT=entrypoint-ui.sh \
$XROAD_HOME/src/security-server/admin-service/application

echo "Building messagelog archiver"
docker build --tag xroad-ss-messagelog-archiver \
--file build-context/Dockerfile \
--build-context entrypoint-ctx=build-context/entrypoint \
--build-context service-ctx=build-context/service-context/messagelog-archiver \
--build-arg JAR_FILE=build/libs/*.jar \
--build-arg ENTRYPOINT_SCRIPT=entrypoint.sh \
$XROAD_HOME/src/addons/messagelog/messagelog-archiver/application

echo "Building monitor"
docker build --tag xroad-ss-monitor \
--file build-context/Dockerfile \
--build-context entrypoint-ctx=build-context/entrypoint \
--build-context service-ctx=build-context/service-context/monitor \
--build-arg JAR_FILE=build/libs/*.jar \
--build-arg ENTRYPOINT_SCRIPT=entrypoint.sh \
$XROAD_HOME/src/monitor/application

echo "Building op-monitor"
docker build --tag xroad-ss-op-monitor \
--file build-context/Dockerfile \
--build-context entrypoint-ctx=build-context/entrypoint \
--build-context service-ctx=build-context/service-context/op-monitor \
--build-arg JAR_FILE=build/libs/*.jar \
--build-arg ENTRYPOINT_SCRIPT=entrypoint.sh \
$XROAD_HOME/src/op-monitor-daemon/application

echo "Building ds-data-plane"
docker build --tag xroad-ss-ds-data-plane \
--file build-context/Dockerfile \
--build-context entrypoint-ctx=build-context/entrypoint \
--build-context service-ctx=build-context/service-context/ds-data-plane \
--build-arg JAR_FILE=build/libs/*.jar \
--build-arg ENTRYPOINT_SCRIPT=entrypoint.sh \
$XROAD_HOME/src/security-server/ds/runtime/data-plane

echo "Building ds-control-plane"
docker build --tag xroad-ss-ds-control-plane \
--file build-context/Dockerfile \
--build-context entrypoint-ctx=build-context/entrypoint \
--build-context service-ctx=build-context/service-context/ds-control-plane \
--build-arg JAR_FILE=build/libs/*.jar \
--build-arg ENTRYPOINT_SCRIPT=entrypoint.sh \
$XROAD_HOME/src/security-server/ds/runtime/control-plane

echo "Building ds-identity-hub"
docker build --tag xroad-ss-ds-identity-hub \
--file build-context/Dockerfile \
--build-context entrypoint-ctx=build-context/entrypoint \
--build-context service-ctx=build-context/service-context/ds-identity-hub \
--build-arg JAR_FILE=build/libs/*.jar \
--build-arg ENTRYPOINT_SCRIPT=entrypoint.sh \
$XROAD_HOME/src/security-server/ds/runtime/identity-hub

echo "Building ds-data-plane-db-init"
docker build --tag xroad-ss-ds-data-plane-db-init \
--build-arg CHANGELOG_FILE=ds-data-plane-changelog.xml \
--build-context changelog=$XROAD_HOME/src/security-server/ds/runtime/data-plane/src/main/resources/liquibase/ \
init_db/

echo "Building ds-control-plane-db-init"
docker build --tag xroad-ss-ds-control-plane-db-init \
--build-arg CHANGELOG_FILE=ds-control-plane-changelog.xml \
--build-context changelog=$XROAD_HOME/src/security-server/ds/runtime/control-plane/src/main/resources/liquibase/ \
init_db/

echo "Building ds-identity-hub-db-init"
docker build --tag xroad-ss-ds-identity-hub-db-init \
--build-arg CHANGELOG_FILE=ds-identity-hub-changelog.xml \
--build-context changelog=$XROAD_HOME/src/security-server/ds/runtime/identity-hub/src/main/resources/liquibase/ \
init_db/