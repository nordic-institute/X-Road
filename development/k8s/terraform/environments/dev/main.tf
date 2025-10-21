provider "helm" {
  kubernetes = {
    config_path = pathexpand(var.kubeconfig_path)
  }
}

module "kind_cluster" {
  source       = "../../modules/kind"

  kind_cluster_name = "xroad-dev-cluster"
  kubeconfig_path = var.kubeconfig_path
  containerd_config_patches = [
    <<-EOF
    [plugins."io.containerd.grpc.v1.cri".registry.mirrors."localhost:5555"]
      endpoint = ["http://host.docker.internal:5555"]
    EOF
  ]
}

module "openbao" {
  source      = "../../modules/openbao"

  depends_on = [
    module.kind_cluster
  ]

  namespace = var.security_server_namespace
  openbao_init_chart_repo = null
  openbao_init_chart = "${path.module}/../../../../../deployment/security-server/k8s/charts/openbao-init"
  openbao_init_chart_version = null

  openbao_db_override_values = yamldecode(file("${path.module}/override-values/openbao-db-values.yaml"))
  openbao_override_values = yamldecode(file("${path.module}/override-values/openbao-values.yaml"))
}

module "cs_service_bridge" {
  source        = "../../modules/external-service-bridge"
  name          = "xrd-cs"

  namespace = var.security_server_namespace
  external_service_bridge_chart_repo = null
  external_service_bridge_chart = "${path.module}/../../../../../deployment/security-server/k8s/charts/external-service-bridge"
  external_service_bridge_chart_version = null

  external_host = "host.docker.internal"
  ports = [
    {
      name       = "http"
      port       = 80
      targetPort = 3010
    },
    {
      name       = "https"
      port       = 443
      targetPort = 3015
    },
    {
      name       = "registration"
      port       = 4001
      targetPort = 3030
    },
    {
      name       = "management"
      port       = 4002
      targetPort = 3035
    }
  ]

  depends_on = [
    module.kind_cluster
  ]
}

module "ca_service_bridge" {
  source        = "../../modules/external-service-bridge"
  name          = "xrd-ca"

  namespace = var.security_server_namespace
  external_service_bridge_chart_repo = null
  external_service_bridge_chart = "${path.module}/../../../../../deployment/security-server/k8s/charts/external-service-bridge"
  external_service_bridge_chart_version = null

  external_host = "host.docker.internal"
  ports = [
    {
      name       = "ca"
      port       = 8888
      targetPort = 4002
    },
    {
      name       = "tsa"
      port       = 8899
      targetPort = 4003
    },
  ]

  depends_on = [
    module.kind_cluster
  ]
}

module "ss0_service_bridge" {
  source        = "../../modules/external-service-bridge"
  name          = "xrd-ss0"

  namespace = var.security_server_namespace
  external_service_bridge_chart_repo = null
  external_service_bridge_chart = "${path.module}/../../../../../deployment/security-server/k8s/charts/external-service-bridge"
  external_service_bridge_chart_version = null

  external_host = "host.docker.internal"
  ports = [
    {
      name       = "proxy"
      port       = 5500
      targetPort = 3230
    },
    {
      name       = "proxy-ocsp"
      port       = 5577
      targetPort = 3240
    },
  ]

  depends_on = [
    module.kind_cluster
  ]
}

module "security-server" {
  source       = "../../modules/security-server"

  depends_on = [
    module.openbao,
    module.cs_service_bridge,
    module.ca_service_bridge,
    module.ss0_service_bridge
  ]

  namespace = var.security_server_namespace
  security_server_chart_repo = null
  security_server_chart = "${path.module}/../../../../../deployment/security-server/k8s/charts/security-server"
  security_server_chart_version = null

  serverconf_db_override_values = yamldecode(file("${path.module}/override-values/serverconf-db-values.yaml"))
  messagelog_db_override_values = yamldecode(file("${path.module}/override-values/messagelog-db-values.yaml"))
  opmonitor_db_override_values = yamldecode(file("${path.module}/override-values/opmonitor-db-values.yaml"))
  security_server_override_values = yamldecode(file("${path.module}/override-values/security-server-values.yaml"))
}