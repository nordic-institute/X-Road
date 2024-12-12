variable "namespace" {
  type = string
}

variable "environment" {
  description = "Environment name (dev, prod)"
  type        = string
}

variable "images_loaded" {
  description = "Dependency marker for images being loaded"
  type        = string
}

variable "postgres_serverconf_username" {
  description = "PostgreSQL serverconf username"
  type        = string
  default     = "serverconf"
}

variable "postgres_serverconf_password" {
  description = "PostgreSQL serverconf password"
  type        = string
  sensitive   = true
}

variable "postgres_messagelog_username" {
  description = "PostgreSQL messagelog username"
  type        = string
  default     = "messagelog"
}

variable "postgres_messagelog_password" {
  description = "PostgreSQL messagelog password"
  type        = string
  sensitive   = true
}