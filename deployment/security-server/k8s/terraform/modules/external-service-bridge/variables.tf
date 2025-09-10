variable "namespace" {
  type = string
  default = "ss"
}

variable "name" {
  type = string
}


variable "ports" {
  type = list(object({
    name       = string
    port       = number
    targetPort = number
  }))
  description = "List of port mappings"

  validation {
    condition     = length(var.ports) > 0
    error_message = "At least one port mapping must be defined"
  }
}

variable "external_host" {
  type = string
}
