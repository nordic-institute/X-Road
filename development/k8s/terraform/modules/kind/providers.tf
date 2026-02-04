terraform {
  required_version = ">= 1.10.6"
  required_providers {
    kind = {
      source = "tehcyx/kind"
      version = "0.9.0"
    }
    null = {
      source  = "hashicorp/null"
      version = "3.2.3"
    }
  }
}