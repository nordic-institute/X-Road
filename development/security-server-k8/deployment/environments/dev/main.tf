terraform {
  required_version = ">=1.8.0"
}

locals {
  kubeconfig_path = module.kind.kubeconfig_path
}

module "kind" {
  source = "../../modules/kind"
  cluster_name = "xroad-dev"
}

module "xroad" {
  source = "../../xroad"

  environment = "dev"
  images_loaded = module.kind.images_loaded

  openbao_dev = true
  postgres_serverconf_password = "dev-secret"
  postgres_messagelog_password = "dev-secret"

  providers = {
    kubernetes = kubernetes
    helm = helm
  }

}