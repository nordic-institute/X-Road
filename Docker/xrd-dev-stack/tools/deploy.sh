#!/bin/bash

deploy_module() {
  local module_name=$1
  shift
  local -a containers=("$@")
  local jar_path
  local service_name
  local target_path

  case $module_name in
  "proxy")
    jar_path="$XROAD_HOME/src/proxy/application/build/libs/proxy-1.0.jar"
    service_name="xroad-proxy"
    ;;
  "messagelog-addon")
    jar_path="$XROAD_HOME/src/addons/messagelog/messagelog-addon/build/libs/messagelog-addon.jar"
    service_name="xroad-proxy"
    ;;
  "hwtoken-addon")
    jar_path="$XROAD_HOME/src/addons/hwtoken/build/libs/hwtoken-1.0.jar"
    service_name="xroad-signer"
    target_path="usr/share/xroad/jlib/addon/signer"
    ;;
  "metaservice-addon")
    jar_path="$XROAD_HOME/src/addons/metaservice/build/libs/metaservice-1.0.jar"
    service_name="xroad-proxy"
    ;;
  "proxy-ui-api")
    jar_path="$XROAD_HOME/src/security-server/admin-service/application/build/libs/proxy-ui-api-1.0.jar"
    service_name="xroad-proxy-ui-api"
    ;;
  "signer")
    jar_path="$XROAD_HOME/src/signer/application/build/libs/signer-1.0.jar"
    service_name="xroad-signer"
    ;;
  "configuration-client")
    jar_path="$XROAD_HOME/src/configuration-client/application/build/libs/configuration-client-1.0.jar"
    service_name="xroad-confclient"
    ;;
  "op-monitor-daemon")
    jar_path="$XROAD_HOME/src/op-monitor-daemon/application/build/libs/op-monitor-daemon-1.0.jar"
    service_name="all"
    ;;
  "cs-admin-service")
    jar_path="$XROAD_HOME/src/central-server/admin-service/application/build/libs/centralserver-admin-service-1.0.jar"
    service_name="xroad-center"
    ;;
  "cs-management-service")
    jar_path="$XROAD_HOME/src/central-server/management-service/application/build/libs/centralserver-management-service-1.0.jar"
    service_name="xroad-center-management-service"
    ;;
  "cs-registration-service")
    jar_path="$XROAD_HOME/src/central-server/registration-service/build/libs/centralserver-registration-service-1.0.jar"
    service_name="xroad-center-registration-service"
    ;;
  *)
    echo "Unknown module: $module_name"
    return 1
    ;;
  esac

  for container in "${containers[@]}"; do
    docker cp "$jar_path" "$container:${target_path:-/usr/share/xroad/jlib/}"
    docker exec -it "$container" supervisorctl restart "$service_name"
  done
}

set -o xtrace

case $1 in
"proxy" | "messagelog-addon" | "metaservice-addon" | "proxy-ui-api" | "configuration-client" | "op-monitor-daemon")
  deploy_module "$1" "ss0" "ss1"
  ;;
"signer" | "hwtoken-addon")
  deploy_module "$1" "ss0" "ss1" "cs"
  ;;
"cs-admin-service" | "cs-management-service" | "cs-registration-service")
  deploy_module "$1" "cs"
  ;;
*)
  echo "Usage: $0 [modulename] [host]"
  exit 1
  ;;
esac

set +o xtrace
