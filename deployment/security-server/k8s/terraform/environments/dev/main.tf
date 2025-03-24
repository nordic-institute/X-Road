
module "kind-cluster" {
  source       = "../../modules/kind"

  kind_cluster_name = "xroad-cluster"
  kube_config_path = var.kube_config_path
  images_registry = "localhost:5555"
}

module "openbao" {
  source      = "../../modules/openbao"

  depends_on = [
    module.kind-cluster
  ]

  openbao_dev = false
}

module "cs-service-bridge" {
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
    module.kind-cluster
  ]
}

module "ca-service-bridge" {
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
    module.kind-cluster
  ]
}

module "ss0-service-bridge" {
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
    module.kind-cluster
  ]
}

module "security-server" {
  source       = "../../modules/security-server"

  depends_on = [
    module.openbao,
    module.cs-service-bridge,
    module.ca-service-bridge,
    module.ss0-service-bridge
  ]

  serverconf_db_postgres_password = "secret"
  messagelog_db_postgres_password = "secret"
  serverconf_db_user_password="secret"
  messagelog_db_user_password="secret"
  configuration_client_update_interval = "10"
}