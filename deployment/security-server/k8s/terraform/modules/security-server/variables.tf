variable "namespace" {
  type = string
  default     = "ss"
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
  description = "Serverconf DB user's password"
  type        = string
  sensitive   = true
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

variable "op_monitor_enabled" {
  description = "Configuration client update interval"
  type        = bool
  default     = false
}

variable "opmonitor_db_postgres_password" {
  description = "Postgres superuser's password for opmonitor DB"
  type        = string
  sensitive   = true
}

variable "opmonitor_db_user" {
  description = "Opmonitor DB user"
  type        = string
  default     = "opmonitor"
}

variable "opmonitor_db_user_password" {
  description = "Opmonitor's DB user's password"
  type        = string
  sensitive   = true
}

variable "configuration_client_update_interval" {
  description = "Configuration client update interval"
  type        = number
  default     = 60
}
