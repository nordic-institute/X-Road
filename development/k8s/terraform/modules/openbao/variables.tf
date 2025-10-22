variable "namespace" {
  type = string
}

variable "openbao_db_user" {
  description = "OpenBao DB user"
  type        = string
  default     = "openbao"
}

variable "openbao_db_user_password" {
  description = "OpenBao DB user's password"
  type        = string
  sensitive   = true
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
  default     = "8.0.0-beta1"
}