variable "namespace" {
  type = string
  default     = "ss"
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