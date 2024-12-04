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
}

module "xroad" {
  source = "../../xroad"

  namespace   = "ss"
  environment = "dev"
  images_loaded = module.kind.images_loaded

  postgres_serverconf_password = "dev-secret"
  postgres_messagelog_password = "dev-secret"

  providers = {
    kubernetes = kubernetes
    helm       = helm
  }

  depends_on = [
    module.openbao
  ]
}