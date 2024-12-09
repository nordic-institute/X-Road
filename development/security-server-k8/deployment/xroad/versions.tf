terraform {
  required_providers {
    kubernetes = {
      source = "hashicorp/kubernetes"
    }
    helm = {
      source = "hashicorp/helm"
    }
  }
}

locals {
  versions = {
    postgres = {
      engine = "17"
      chart  = "13.2.28"
    }

  }
}
