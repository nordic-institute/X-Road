variable "cluster_name" {
  type = string
  default = "xroad-cluster"
  description = "Name of the Kind cluster"
}

variable "images" {
  type    = list(string)
  default = [
    "init-runner:latest",
    "xroad-ss-ui:latest",
    "xroad-ss-config:latest",
    "xroad-ss-confclient:latest",
    "xroad-ss-signer:latest",
    "xroad-ss-proxy:latest"
  ]
  description = "List of Docker images to load into Kind cluster"
}