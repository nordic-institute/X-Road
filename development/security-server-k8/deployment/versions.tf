terraform {
  required_providers {
    helm = {
      source  = "hashicorp/helm"
      version = "2.34.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "2.23.0"
    }
    null = {
      source  = "hashicorp/null"
      version = "3.2.3"
    }
  }
}