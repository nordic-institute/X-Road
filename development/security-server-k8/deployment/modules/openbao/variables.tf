variable "namespace" {
  type = string
}

variable "openbao_dev" {
  description = "OpenBAO dev"
  type        = bool
  default = false
}

variable "openbao_version" {
  type    = string
  default = "2.1.0"
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