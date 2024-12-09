#!/bin/bash -e
set -e

origin="$(pwd)"

gradleModule=""
gradleArgs="clean build -xtest -xcheckstyleMain -xcheckstyleTest "
case $1 in
"addon-message-log-archiver")
  gradleModule="addons/messagelog/messagelog-archiver"
  ;;
"configuration-service")
  gradleModule="security-server/configuration-service"
  ;;
"proxy")
  gradleModule="proxy"
  gradleArgs+="-xintTest -xintegrationTest"
  ;;
"configuration-client")
  gradleModule="configuration-client"
  ;;
"signer")
  gradleModule="signer"
  gradleArgs+="-xintTest"
  ;;
"signer-console")
  gradleModule="signer-console"
  ;;
"proxy-ui-api")
  gradleModule="security-server/admin-service"
  gradleArgs+="-xintTest"
  ;;
"cs-admin-service")
  gradleModule="central-server/admin-service"
  gradleArgs+="-xintTest"
  ;;
"cs-management-service")
  gradleModule="central-server/management-service"
  gradleArgs+="-xintTest"
  ;;
"ds-control-plane")
  gradleModule="security-server/ds/runtime/control-plane"
  ;;
"ds-data-plane")
  gradleModule="security-server/ds/runtime/data-plane"
  ;;
"ds-ih")
  gradleModule="security-server/ds/runtime/identity-hub"
  ;;
"cs-catalog-service")
  gradleModule="central-server/ds-catalog-service"
  ;;
"cs-credential-service")
  gradleModule="central-server/ds-credential-service"
  ;;
"messagelog-addon")
  gradleModule="addons/messagelog/messagelog-addon"
  ;;
"asicverifier")
  gradleModule="asicverifier"
  ;;
"op-monitor-daemon")
  gradleModule="op-monitor-daemon"
  ;;
"monitor")
  gradleModule="monitor"
  ;;
esac

cd ../../src/
set -o xtrace
./gradlew $gradleArgs -p $gradleModule
set +o xtrace
cd "$origin"
