variable "kind_cluster_name" {
  type        = string
  description = "Name of the Kind cluster"
}

variable "kubeconfig_path" {
  type        = string
  description = "kube config file path"
}

variable "containerd_config_patches" {
    type        = list(string)
    description = "Custom containerd config patches for the Kind cluster"
    default     = []
}
