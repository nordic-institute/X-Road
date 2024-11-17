#!/bin/bash

deploy_module() {
  local module_name=$1
  shift
  local -a containers=("$@")
  local jar_path
  local service_name
  local target_path="/usr/share/xroad/jlib/"

  case $module_name in
  "proxy")
    jar_path="$XROAD_HOME/src/proxy/application/build/libs/proxy-1.0.jar"
    service_name="xroad-proxy"
    ;;
  "messagelog-addon")
    jar_path="$XROAD_HOME/src/addons/messagelog/messagelog-addon/build/libs/messagelog-addon.jar"
    target_path="${target_path}addon/proxy/"
    service_name="xroad-proxy"
    ;;
  "metaservice-addon")
    jar_path="$XROAD_HOME/src/addons/metaservice/build/libs/metaservice-1.0.jar"
    target_path="${target_path}addon/proxy/"
    service_name="xroad-proxy"
    ;;
  "proxy-ui-api")
    jar_path="$XROAD_HOME/src/security-server/admin-service/application/build/libs/proxy-ui-api-1.0.jar"
    service_name="xroad-proxy-ui-api"
    ;;
  "signer")
    jar_path="$XROAD_HOME/src/signer/application/build/libs/signer-1.0.jar"
    service_name="all"
    ;;
  "configuration-client")
    jar_path="$XROAD_HOME/src/configuration-client/application/build/libs/configuration-client-1.0.jar"
    service_name="xroad-confclient"
    ;;
  "asicverifier")
    jar_path="$XROAD_HOME/src/asicverifier/build/libs/asicverifier.jar"
    ;;
  "op-monitor-daemon")
    jar_path="$XROAD_HOME/src/op-monitor-daemon/application/build/libs/op-monitor-daemon-1.0.jar"
    service_name="all"
    ;;
  "monitor")
    jar_path="$XROAD_HOME/src/monitor/application/build/libs/monitor-1.0.jar"
    service_name="xroad-monitor"
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
  "edc-control-plane")
    jar_path="$XROAD_HOME/src/security-server/edc/runtime/control-plane/build/libs/edc-control-plane-1.0.jar"
    service_name="xroad-edc-control-plane"
    ;;
  "edc-data-plane")
    jar_path="$XROAD_HOME/src/security-server/edc/runtime/data-plane/build/libs/edc-data-plane-1.0.jar"
    service_name="xroad-edc-data-plane"
    ;;
  "edc-ih")
    jar_path="$XROAD_HOME/src/security-server/edc/runtime/identity-hub/build/libs/edc-identity-hub-1.0.jar"
    service_name="xroad-edc-ih"
    ;;
  "cs-catalog-service")
    jar_path="$XROAD_HOME/src/central-server/ds-catalog-service/build/libs/ds-catalog-service-1.0.jar"
    service_name="xroad-edc-catalog-service"
    ;;
  "cs-credential-service")
    jar_path="$XROAD_HOME/src/central-server/ds-credential-service/build/libs/ds-credential-service-1.0.jar"
    service_name="xroad-edc-credential-service"
    ;;
  *)
    echo "Unknown module: $module_name"
    return 1
    ;;
  esac

  for container in "${containers[@]}"; do
    docker cp "$jar_path" "$container:$target_path"
    if [ -n "$service_name" ]; then
      docker exec -it "$container" supervisorctl restart "$service_name"
    fi
  done
}

set -o xtrace

case $1 in
"proxy" | "messagelog-addon" | "metaservice-addon" | "proxy-ui-api" | "configuration-client" | "asicverifier" | "op-monitor-daemon" | "monitor" | "edc-control-plane" | "edc-data-plane")
  deploy_module "$1" "ss0" "ss1"
  ;;
"cs-admin-service" | "cs-management-service" | "cs-registration-service" | "cs-catalog-service" | "cs-credential-service")
  deploy_module "$1" "cs"
  ;;
"signer" | "edc-ih")
  deploy_module "$1" "ss0" "ss1"
  deploy_module "$1" "cs"
  ;;
*)
  echo "Usage: $0 [modulename] [host]"
  exit 1
  ;;
esac

set +o xtrace
