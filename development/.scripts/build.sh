#!/bin/bash -e
set -e

origin="$(pwd)"

gradleModule=""
gradleArgs="clean build -x check "
case $1 in
"proxy")
  gradleModule="service/proxy"
  ;;
"configuration-client")
  gradleModule="service/configuration-client"
  ;;
"signer")
  gradleModule="signer"
  ;;
"proxy-ui-api")
  gradleModule="security-server/admin-service"
  ;;
"cs-admin-service")
  gradleModule="central-server/admin-service"
  ;;
"cs-management-service")
  gradleModule="central-server/management-service"
  ;;
esac

cd ../../src/
set -o xtrace
./gradlew $gradleArgs -p $gradleModule
set +o xtrace
cd "$origin"
