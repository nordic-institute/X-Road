provider "kubernetes" {
  config_path = module.kind.kubeconfig_path
}

provider "helm" {
  debug = true
  kubernetes {
    config_path = module.kind.kubeconfig_path
  }
}