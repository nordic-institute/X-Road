#!/bin/bash -e
set -e

SCRIPT_DIR=$(dirname "$(realpath "${BASH_SOURCE[0]}")")
origin="$(pwd)"

gradleModule=""
gradleArgs="clean build -x check "
case $1 in
"proxy")
  gradleModule="service/proxy"
  ;;
"addon-messagelog")
  gradleModule="addons/messagelog/messagelog-addon"
  ;;
"addon-metaservice")
  gradleModule="addons/metaservice"
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

cd "$SCRIPT_DIR"/../../src/
set -o xtrace
./gradlew $gradleArgs -p $gradleModule
set +o xtrace
cd "$origin"
