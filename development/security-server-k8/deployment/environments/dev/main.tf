terraform {
  required_version = ">=1.8.0"
}

locals {
  kubeconfig_path = module.kind.kubeconfig_path
}

resource "kubernetes_namespace" "ss" {
  metadata {
    name = "ss"
  }
}

module "kind" {
  source       = "../../modules/kind"
  cluster_name = "xroad-dev"
}

module "openbao" {
  source      = "../../modules/openbao"
  namespace   = "ss"
  openbao_dev = false

  postgres_serverconf_username = "serverconf"
  postgres_serverconf_password = "serverconf-password"

  postgres_messagelog_username = "messagelog"
  postgres_messagelog_password = "messagelog-password"
}

module "cs_service_bridge" {
  source        = "../../modules/external_service_bridge"
  namespace     = "ss"
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
    }
  ]
}

module "xroad" {
  source = "../../xroad"

  namespace     = "ss"
  environment   = "dev"
  images_loaded = module.kind.images_loaded

  postgres_serverconf_username = "serverconf"
  postgres_serverconf_password = "serverconf-password"

  postgres_messagelog_username = "messagelog"
  postgres_messagelog_password = "messagelog-password"

  postgres_ds_username = "ds-user"
  postgres_ds_password = "ds-password"

  #TODO: data spaces is not yet usable in k8
  data_spaces_enabled = false

  providers = {
    kubernetes = kubernetes
    helm       = helm
  }

  depends_on = [
    module.openbao,
    module.cs_service_bridge
  ]

}
