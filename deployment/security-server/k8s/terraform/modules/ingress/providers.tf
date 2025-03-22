terraform {
  required_version = ">= 1.9.0"
  required_providers {
    helm = {
      source  = "hashicorp/helm"
      version = "2.17.0"
    }
    null = {
      source  = "hashicorp/null"
      version = "3.2.3"
    }
  }
}