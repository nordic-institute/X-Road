variable "kubeconfig_path" {
  type        = string
  description = "kube config file path"
  default = "~/.kube/config"
}

variable "security_server_namespace" {
  type = string
  default = "ss"
}