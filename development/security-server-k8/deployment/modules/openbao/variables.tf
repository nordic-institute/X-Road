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