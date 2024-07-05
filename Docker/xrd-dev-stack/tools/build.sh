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
esac

cd "$XROAD_HOME"/src/
set -o xtrace
./gradlew $gradleArgs -p $gradleModule
set +o xtrace
cd "$origin"
