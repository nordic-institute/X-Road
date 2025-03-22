variable "namespace" {
  type = string
  default     = "ss"
}

variable "target_service" {
  type        = string
  description = "Service name to route traffic to"
  default     = "proxy-ui-api"
}

variable "target_port" {
  type        = number
  description = "Service port to route traffic to"
  default     = 4000
}