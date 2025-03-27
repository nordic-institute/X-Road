variable "kind_cluster_name" {
  type        = string
  description = "Name of the Kind cluster"
}

variable "kube_config_path" {
  type        = string
  description = "kube config file path"
}

variable "images_registry" {
  type        = string
  description = "URL of docker registry to load images from"
}

variable "images" {
  type = list(string)
  default = [
    "ss-baseline-runtime",
    "ss-baseline-ui-runtime",
    "ss-db-messagelog-init",
    "ss-db-serverconf-init",
    "ss-proxy",
    "ss-configuration-client",
    "ss-signer",
    "ss-proxy-ui-api",
    "ss-message-log-archiver",
    "ss-monitor"
  ]
  description = "List of Docker images to load into Kind cluster"
}
