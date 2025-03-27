output "kubeconfig" {
  value = kind_cluster.xroad-cluster.kubeconfig
}

output "kubeconfig_path" {
  value = kind_cluster.xroad-cluster.kubeconfig_path
  description = "Path to the kubeconfig file for the Kind cluster"
}

