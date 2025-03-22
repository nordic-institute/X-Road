variable "namespace" {
  type = string
  default     = "ss"
}

variable "serverconf_db_name" {
  description = "Serverconf DB name"
  type        = string
  default     = "serverconf"
}

variable "serverconf_db_postgres_password" {
  description = "Postgres superuser's password for serverconf DB"
  type        = string
  sensitive   = true
}

variable "serverconf_db_user" {
  description = "Serverconf DB user"
  type        = string
  default     = "serverconf"
}

variable "serverconf_db_user_password" {
  description = "Serverconf DB uses's password"
  type        = string
  sensitive   = true
}

variable "messagelog_db_name" {
  description = "Messagelog DB name"
  type        = string
  default     = "messagelog"
}

variable "messagelog_db_postgres_password" {
  description = "Postgres superuser's password for messagelog DB"
  type        = string
  sensitive   = true
}

variable "messagelog_db_user" {
  description = "Messagelog DB user"
  type        = string
  default     = "messagelog"
}

variable "messagelog_db_user_password" {
  description = "Messagelog's DB user's password"
  type        = string
  sensitive   = true
}

variable "configuration_client_update_interval" {
  description = "Configuration client update interval"
  type        = string
  default     = "60"
}
