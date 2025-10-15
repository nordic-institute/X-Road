provider "helm" {
  kubernetes = {
    config_path = var.kubeconfig_path
  }
}

module "kind_cluster" {
  source       = "../../modules/kind"

  kind_cluster_name = "xroad-cluster"
  kubeconfig_path = var.kubeconfig_path
}

module "openbao" {
  source      = "../../modules/openbao"

  depends_on = [
    module.kind_cluster
  ]

  openbao_db_user_password="secret"
}

module "cs_service_bridge" {
  source        = "../../modules/external-service-bridge"
  name          = "xrd-cs"
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

  serverconf_db_postgres_password = "admin_secret"
  messagelog_db_postgres_password = "admin_secret"
  serverconf_db_user_password="secret"
  messagelog_db_user_password="secret"
  configuration_client_update_interval = "10"
  op_monitor_enabled = true
  opmonitor_db_postgres_password="admin_secret"
  opmonitor_db_user_password="secret"
  proxy_ui_superuser_password = "$argon2id$v=19$m=16384,t=2,p=1$YXF3YXN6eHh6c2F3cQ$+llp8EbxlqZaF2uO/BLoFLwfqxe1Yn6BvC/DOegq6A0"  # argon2 hash of "secret"
}