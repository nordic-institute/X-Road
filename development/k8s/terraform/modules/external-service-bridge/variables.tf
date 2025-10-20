variable "namespace" {
  type = string
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

variable "external_service_bridge_chart_repo" {
  description = "OpenBao init chart repository"
  type        = string
  default     = "oci://artifactory.niis.org/xroad8-snapshot-helm"
}

variable "external_service_bridge_chart" {
  description = "OpenBao init chart"
  type        = string
  default     = "external-service-bridge"
}

variable "external_service_bridge_chart_version" {
  description = "OpenBao init chart version"
  type        = string
  default     = "8.0.0-beta1-SNAPSHOT"
}