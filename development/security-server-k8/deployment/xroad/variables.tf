variable "namespace" {
  type = string
}

variable "environment" {
  description = "Environment name (dev, prod)"
  type        = string
}

variable "images_loaded" {
  description = "Dependency marker for images being loaded"
  type = string
}

variable "openbao_dev" {
  description = "OpenBAO dev"
  type        = bool
  default = false
}

variable "postgres_serverconf_password" {
  description = "PostgreSQL serverconf password"
  type        = string
  sensitive   = true
}

variable "postgres_messagelog_password" {
  description = "PostgreSQL messagelog password"
  type        = string
  sensitive   = true
}