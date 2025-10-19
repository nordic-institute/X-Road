variable "namespace" {
  type = string
}

variable "serverconf_db_override_values" {
  description = "Override values for the serverconf DB Helm chart"
  type        = any
}

variable "messagelog_db_override_values" {
  description = "Override values for the messagelog DB Helm chart"
  type        = any
}

variable "opmonitor_db_override_values" {
  description = "Override values for the opmonitor DB Helm chart"
  type        = any
}

variable "security_server_override_values" {
  description = "Override values for the security server DB Helm chart"
  type        = any
}

variable "security_server_chart_repo" {
  description = "Security Server chart repository"
  type        = string
  default     = "oci://artifactory.niis.org/xroad8-snapshot-helm"
}

variable "security_server_chart" {
  description = "Security Server chart"
  type        = string
  default     = "security-server"
}

variable "security_server_chart_version" {
  description = "Security Server chart version"
  type        = string
  default     = "8.0.0-beta1-SNAPSHOT"
}
