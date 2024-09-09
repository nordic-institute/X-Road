#!/bin/bash -e
set -e

origin="$(pwd)"

gradleModule=""
gradleArgs="clean build -xtest -xcheckstyleMain -xcheckstyleTest "
case $1 in
"proxy")
  gradleModule="proxy"
  gradleArgs+="-xintTest -xintegrationTest"
  ;;
"configuration-client")
  gradleModule="configuration-client"
  ;;
"signer")
  gradleModule="signer"
  gradleArgs+="-xintTest -xintTest"
  ;;
"proxy-ui-api")
  gradleModule="security-server/admin-service"
  ;;
"cs-admin-service")
  gradleModule="central-server/admin-service"
  gradleArgs+="-xintTest"
  ;;
"cs-management-service")
  gradleModule="central-server/management-service"
  gradleArgs+="-xintTest"
  ;;
"edc-control-plane")
  gradleModule="security-server/edc/runtime/control-plane"
  ;;
"edc-data-plane")
  gradleModule="security-server/edc/runtime/data-plane"
  ;;
"edc-ih")
  gradleModule="security-server/edc/runtime/identity-hub"
  ;;
"cs-edc")
  gradleModule="central-server/ds-catalog-service"
  ;;
"messagelog-addon")
  gradleModule="addons/messagelog/messagelog-addon"
  ;;
"asicverifier")
  gradleModule="asicverifier"
  ;;
esac

cd "$XROAD_HOME"/src/
set -o xtrace
./gradlew $gradleArgs -p $gradleModule
set +o xtrace
cd "$origin"
