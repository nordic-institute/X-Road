variable "cluster_name" {
  type        = string
  default     = "xroad-cluster"
  description = "Name of the Kind cluster"
}

variable "images" {
  type = list(string)
  default = [
    "init-runner:latest",
    "xroad-ss-serverconf-init:latest",
    "xroad-ss-messagelog-init:latest",
    "xroad-ss-ui:latest",
    "xroad-ss-config:latest",
    "xroad-ss-confclient:latest",
    "xroad-ss-signer:latest",
    "xroad-ss-proxy:latest",
    "xroad-ss-messagelog-archiver:latest",
    "xroad-ss-monitor:latest",
    "xroad-ss-op-monitor:latest",
    "xroad-ss-ds-data-plane:latest",
    "xroad-ss-ds-control-plane:latest",
    "xroad-ss-ds-identity-hub:latest",
    "xroad-ss-ds-data-plane-db-init:latest",
    "xroad-ss-ds-control-plane-db-init:latest",
    "xroad-ss-ds-identity-hub-db-init:latest",
    "xroad-ss-proxy-qrk:latest",
  ]
  description = "List of Docker images to load into Kind cluster"
}