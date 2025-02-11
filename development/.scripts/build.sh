#!/bin/bash -e
set -e

origin="$(pwd)"

gradleModule=""
gradleArgs="clean build -xtest -xcheckstyleMain -xcheckstyleTest "
case $1 in
"proxy")
  gradleModule="service/proxy/proxy-application"
  ;;
"configuration-client")
  gradleModule="service/configuration-client/configuration-client-application"
  ;;
"signer")
  gradleModule="service/signer/signer-application"
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

cd ../../src/
set -o xtrace
./gradlew $gradleArgs -p $gradleModule
set +o xtrace
cd "$origin"
