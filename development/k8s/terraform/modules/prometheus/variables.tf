variable "namespace" {
  type = string
}

variable "prometheus_override_values" {
  description = "Override values for the Prometheus Helm chart"
  type        = any
}
