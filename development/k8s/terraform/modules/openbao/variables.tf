variable "namespace" {
  type = string
}

variable "openbao_init_chart_repo" {
  description = "OpenBao init chart repository"
  type        = string
  default     = "oci://artifactory.niis.org/xroad8-snapshot-helm"
}

variable "openbao_init_chart" {
  description = "OpenBao init chart"
  type        = string
  default     = "openbao-init"
}

variable "openbao_init_chart_version" {
  description = "OpenBao init chart version"
  type        = string
  default     = "8.0.0-beta1-SNAPSHOT"
}

variable "openbao_db_override_values" {
  description = "Override values for the OpenBao DB Helm chart"
  type        = any
}

variable "openbao_override_values" {
  description = "Override values for the OpenBao Helm chart"
  type        = any
}
