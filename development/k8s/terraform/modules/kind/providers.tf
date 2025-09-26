terraform {
  required_version = ">= 1.9.0"
  required_providers {
    kind = {
      source = "tehcyx/kind"
      version = "0.8.0"
    }
    null = {
      source  = "hashicorp/null"
      version = "3.2.3"
    }
  }
}